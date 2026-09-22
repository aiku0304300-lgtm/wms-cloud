package com.wms.check.job;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.check.entity.DemoSecondVerificationLog;
import com.wms.check.mapper.DemoSecondVerificationLogMapper;
import com.wms.check.service.DemoSecondVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 对应生产的 RetrySecondVerification 命令：定时扫失败记录，重新跑一遍那个慢操作。
 * 这是"最终一致性"的落地方式——核对本身早就成功提交了，这里只是把没建成的
 * 附属数据尽力补上，重试超过上限就放弃并留痕，不会无限重试下去。
 */
@Component
public class SecondVerificationRetryJob {

    private static final int MAX_RETRY = 3;

    @Autowired
    private DemoSecondVerificationLogMapper logMapper;

    @Autowired
    private DemoSecondVerificationService demoSecondVerificationService;

    @Scheduled(fixedDelay = 10_000)
    public void retryPending() {
        List<DemoSecondVerificationLog> pending = logMapper.selectList(
                new LambdaQueryWrapper<DemoSecondVerificationLog>()
                        .eq(DemoSecondVerificationLog::getRetryStatus, 0)
                        .lt(DemoSecondVerificationLog::getRetryCount, MAX_RETRY));

        for (DemoSecondVerificationLog log : pending) {
            try {
                demoSecondVerificationService.execute(log.getOrderId(), log.getBoxId(), log.getScanOrder());
                log.setRetryStatus(1);
            } catch (Exception e) {
                log.setRetryCount(log.getRetryCount() + 1);
                log.setErrorMsg(e.getMessage());
                if (log.getRetryCount() >= MAX_RETRY) {
                    log.setRetryStatus(2);
                }
            }
            logMapper.updateById(log);
        }
    }
}
