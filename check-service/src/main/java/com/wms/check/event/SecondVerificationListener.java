package com.wms.check.event;

import com.wms.check.entity.DemoSecondVerificationLog;
import com.wms.check.mapper.DemoSecondVerificationLogMapper;
import com.wms.check.service.DemoSecondVerificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * @TransactionalEventListener(phase = AFTER_COMMIT) 的效果：checkBox() 里发布事件那一刻
 * 什么都不会发生，Spring 只是先记下"提交成功后要处理这个事件"。真正调用这个方法是在
 * checkBox() 的事务确认提交之后——如果事务回滚了，这里根本不会被触发，天然保证了
 * "核心逻辑失败就不会牵连出附属逻辑"。
 * <p>
 * @Async 让这个方法在别的线程池里跑，不占用触发提交的那个请求线程，
 * 用户拿到 checkBox() 的响应不用等这 300ms。
 */
@Component
public class SecondVerificationListener {

    @Autowired
    private DemoSecondVerificationService demoSecondVerificationService;

    @Autowired
    private DemoSecondVerificationLogMapper logMapper;

    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onSecondVerification(SecondVerificationEvent event) {
        try {
            demoSecondVerificationService.execute(event.getOrderId(), event.getBoxId(), event.getScanOrder());
        } catch (Exception e) {
            // 失败不往上抛——这里已经是异步线程，抛出去没人接得住，只会静默丢失在日志里。
            // 落库才能让后面的补偿任务找到它。
            DemoSecondVerificationLog log = new DemoSecondVerificationLog();
            log.setOrderId(event.getOrderId());
            log.setBoxId(event.getBoxId());
            log.setScanOrder(event.getScanOrder());
            log.setErrorMsg(e.getMessage());
            log.setRetryStatus(0);
            log.setRetryCount(0);
            logMapper.insert(log);
        }
    }
}
