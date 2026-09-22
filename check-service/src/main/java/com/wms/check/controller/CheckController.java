package com.wms.check.controller;


import com.wms.check.client.InventoryDTO;
import com.wms.check.client.InventoryFeignClient;
import com.wms.check.exception.CheckException;
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

    @ExceptionHandler(CheckException.class)
    public Map<String, Object> handleCheckException(CheckException e) {
        return Map.of("code", e.getError().getCode(), "msg", e.getMessage());
    }

}
