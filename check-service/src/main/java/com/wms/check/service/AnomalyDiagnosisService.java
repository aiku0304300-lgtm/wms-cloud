package com.wms.check.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.check.ai.AiDiagnosisClient;
import com.wms.check.entity.DemoBox;
import com.wms.check.entity.DemoOrder;
import com.wms.check.entity.DemoSecondVerificationLog;
import com.wms.check.exception.CheckError;
import com.wms.check.exception.CheckException;
import com.wms.check.mapper.DemoBoxMapper;
import com.wms.check.mapper.DemoOrderMapper;
import com.wms.check.mapper.DemoSecondVerificationLogMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 面向运维的辅助能力：把 demo_second_verification_log 里某个订单的失败/重试记录
 * 拼成一段人类可读的上下文，丢给 AiDiagnosisClient，换回一段自然语言诊断
 * （根因是什么、该往哪查），省去人工去数据库里一条条翻日志。
 * <p>
 * 定位很明确：这是核对主流程之外的"锦上添花"功能，不改、不碰 checkBox() 那条核心链路——
 * 单独开一个只读接口，AI 不可用时 AiDiagnosisClient 那层已经兜了底，
 * 不会把 AI 的不确定性引入到核心业务里。
 */
@Service
public class AnomalyDiagnosisService {

    @Autowired
    private DemoOrderMapper demoOrderMapper;

    @Autowired
    private DemoBoxMapper demoBoxMapper;

    @Autowired
    private DemoSecondVerificationLogMapper logMapper;

    @Autowired
    private AiDiagnosisClient aiDiagnosisClient;

    public String diagnose(String orderNo) {
        DemoOrder order = demoOrderMapper.selectOne(
                new LambdaQueryWrapper<DemoOrder>().eq(DemoOrder::getOrderNo, orderNo));
        if (order == null) {
            throw new CheckException(CheckError.ORDER_NOT_FOUND);
        }

        List<DemoSecondVerificationLog> logs = logMapper.selectList(
                new LambdaQueryWrapper<DemoSecondVerificationLog>()
                        .eq(DemoSecondVerificationLog::getOrderId, order.getId())
                        .orderByDesc(DemoSecondVerificationLog::getCreateTime)
                        .last("LIMIT 20"));

        if (logs.isEmpty()) {
            return "订单 " + orderNo + " 近期没有二次核对失败/重试记录，暂无异常。";
        }

        return aiDiagnosisClient.diagnose(buildPrompt(orderNo, logs));
    }

    private String buildPrompt(String orderNo, List<DemoSecondVerificationLog> logs) {
        StringBuilder sb = new StringBuilder();
        sb.append("订单号：").append(orderNo).append("\n");
        sb.append("以下是该订单最近的二次核对失败/重试记录（最多20条，按时间倒序）：\n");
        for (DemoSecondVerificationLog entry : logs) {
            DemoBox box = demoBoxMapper.selectById(entry.getBoxId());
            String boxNo = box == null ? "(箱子已不存在，boxId=" + entry.getBoxId() + ")" : box.getBoxNo();
            sb.append("- 箱号=").append(boxNo)
                    .append(", 扫描序号=").append(entry.getScanOrder())
                    .append(", 重试状态=").append(describeRetryStatus(entry.getRetryStatus()))
                    .append(", 已重试次数=").append(entry.getRetryCount())
                    .append(", 错误信息=").append(entry.getErrorMsg())
                    .append(", 发生时间=").append(entry.getCreateTime())
                    .append("\n");
        }
        return sb.toString();
    }

    private String describeRetryStatus(Integer status) {
        if (status == null) {
            return "未知";
        }
        return switch (status) {
            case 0 -> "待补偿";
            case 1 -> "补偿成功";
            case 2 -> "重试到上限仍失败";
            default -> "未知(" + status + ")";
        };
    }
}
