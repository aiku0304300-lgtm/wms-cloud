package com.wms.check.controller;


import com.wms.check.client.InventoryDTO;
import com.wms.check.client.InventoryFeignClient;
import com.wms.check.exception.CheckException;
import com.wms.check.service.AnomalyDiagnosisService;
import com.wms.check.service.DemoOrderService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/check")
public class CheckController {

    @Autowired
    private InventoryFeignClient inventoryFeignClient;

    @Autowired
    private DemoOrderService demoOrderService;

    @Autowired
    private AnomalyDiagnosisService anomalyDiagnosisService;

    @GetMapping("/stock")
    public InventoryDTO checkStock(@RequestParam("skuCode") String skuCode){
        return inventoryFeignClient.getBySku(skuCode);
    }

    /**
     * 模拟扫码枪扫一个箱号。成功返回分配到的扫描序号。
     */
    @PostMapping("/box")
    public Map<String, Object> checkBox(@RequestParam("orderNo") String orderNo,
                                        @RequestParam("boxNo") String boxNo) {
        int scanOrder = demoOrderService.checkBox(orderNo, boxNo);
        return Map.of("code", 0, "msg", "核对成功", "scanOrder", scanOrder);
    }

    /**
     * AI 辅助诊断：把某个订单最近的二次核对失败/重试记录喂给大模型，换一段人话诊断，
     * 省去运维去数据库里一条条翻 demo_second_verification_log。
     * 只读、不碰核对主流程；AI 不可用时 AnomalyDiagnosisService 内部已经兜底，这里不用关心。
     */
    @GetMapping("/diagnose")
    public Map<String, Object> diagnose(@RequestParam("orderNo") String orderNo) {
        String diagnosis = anomalyDiagnosisService.diagnose(orderNo);
        return Map.of("code", 0, "msg", "ok", "diagnosis", diagnosis);
    }

    @ExceptionHandler(CheckException.class)
    public Map<String, Object> handleCheckException(CheckException e) {
        return Map.of("code", e.getError().getCode(), "msg", e.getMessage());
    }

}
