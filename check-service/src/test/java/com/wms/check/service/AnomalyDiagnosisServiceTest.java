package com.wms.check.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.check.entity.DemoBox;
import com.wms.check.entity.DemoOrder;
import com.wms.check.entity.DemoSecondVerificationLog;
import com.wms.check.mapper.DemoBoxMapper;
import com.wms.check.mapper.DemoOrderMapper;
import com.wms.check.mapper.DemoSecondVerificationLogMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打真实 MySQL，不 mock AI 调用——测试环境没配 ai.api-key（见 test 的 application.yml），
 * 所以这里验证的正是 OpenAiCompatibleDiagnosisClient 的"规则兜底"路径：
 * 没有 key 也能跑通整条链路，返回里带上原始记录，而不是抛异常或返回空。
 */
@SpringBootTest
class AnomalyDiagnosisServiceTest {

    @Autowired
    private AnomalyDiagnosisService anomalyDiagnosisService;

    @Autowired
    private DemoOrderMapper demoOrderMapper;

    @Autowired
    private DemoBoxMapper demoBoxMapper;

    @Autowired
    private DemoSecondVerificationLogMapper logMapper;

    private String orderNo;
    private Long orderId;
    private Long boxId;

    @BeforeEach
    void setUp() {
        orderNo = "DIAG-" + UUID.randomUUID().toString().substring(0, 8);
        DemoOrder order = new DemoOrder();
        order.setOrderNo(orderNo);
        order.setLastScanOrder(1);
        demoOrderMapper.insert(order);
        orderId = order.getId();

        DemoBox box = new DemoBox();
        box.setOrderId(orderId);
        box.setBoxNo("BOX-DIAG-1");
        box.setStatus(1);
        box.setScanOrder(1);
        demoBoxMapper.insert(box);
        boxId = box.getId();
    }

    @AfterEach
    void tearDown() {
        logMapper.delete(new LambdaQueryWrapper<DemoSecondVerificationLog>().eq(DemoSecondVerificationLog::getOrderId, orderId));
        demoBoxMapper.deleteById(boxId);
        demoOrderMapper.deleteById(orderId);
    }

    @Test
    @DisplayName("没有失败记录时，直接返回“暂无异常”，不调用 AI")
    void noLogs_returnsNoAnomalyMessage() {
        String diagnosis = anomalyDiagnosisService.diagnose(orderNo);
        assertTrue(diagnosis.contains("暂无异常"));
    }

    @Test
    @DisplayName("有失败记录时，没配 AI key 会走规则兜底，原始记录要能在返回结果里查到")
    void hasFailureLogs_fallbackContainsRawContext() {
        DemoSecondVerificationLog log = new DemoSecondVerificationLog();
        log.setOrderId(orderId);
        log.setBoxId(boxId);
        log.setScanOrder(4);
        log.setErrorMsg("模拟下游二次核对服务超时");
        log.setRetryStatus(2);
        log.setRetryCount(3);
        logMapper.insert(log);

        String diagnosis = anomalyDiagnosisService.diagnose(orderNo);

        assertTrue(diagnosis.contains("AI 诊断不可用"), "测试环境没配 key，应该走兜底分支");
        assertTrue(diagnosis.contains("BOX-DIAG-1"), "兜底文本应该带上原始箱号，方便人工判断");
        assertTrue(diagnosis.contains("重试到上限仍失败"), "重试状态码要被翻译成人话");
    }
}
