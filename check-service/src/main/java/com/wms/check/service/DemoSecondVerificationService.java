package com.wms.check.service;

import org.springframework.stereotype.Service;

/**
 * 模拟生产的 createSecondVerification()：一个跟核心核对逻辑无关、但耗时的附属操作
 * （生产里是查/建"渠道→单号→箱号"三层二次核对数据，一串串行 I/O，300ms 量级）。
 * 这里不做真实业务，只用 sleep(300) 复现"慢"，用固定条件复现"会失败"，方便观察补偿链路。
 */
@Service
public class DemoSecondVerificationService {

    public void execute(Long orderId, Long boxId, Integer scanOrder) {
        try {
            Thread.sleep(300);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("模拟慢操作被中断", e);
        }

        // 演示用：scanOrder 是 4 的倍数时必现失败，方便你能稳定观察到补偿流程被触发。
        // 生产里失败通常是偶发的网络超时，不会像这样固定命中同一批。
        if (scanOrder != null && scanOrder % 4 == 0) {
            throw new RuntimeException("模拟下游二次核对服务超时（演示用，scanOrder 为 4 的倍数时必现）");
        }
    }
}
