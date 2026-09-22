package com.wms.check.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.check.entity.DemoBox;
import com.wms.check.entity.DemoOrder;
import com.wms.check.exception.CheckError;
import com.wms.check.exception.CheckException;
import com.wms.check.event.SecondVerificationEvent;
import com.wms.check.mapper.DemoBoxMapper;
import com.wms.check.mapper.DemoOrderMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DemoOrderService {

    @Autowired
    private DemoOrderMapper demoOrderMapper;

    @Autowired
    private DemoBoxMapper demoBoxMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    @Autowired
    private DemoSecondVerificationService demoSecondVerificationService;

    /**
     * 扫一个箱号完成核对，返回分配到的扫描序号。
     * <p>
     * 箱号是扫码枪给的入参，不是系统从空闲箱里挑的——这点和 PHP 生产一致。
     * 失败一律抛 CheckException，不重试：CAS 抢不到就是这箱已经被别人核对过了，重试没有意义。
     * <p>
     * @Transactional 在这里有两个作用：出错整体回滚；以及把数据库连接绑到当前线程，
     * 保证 incrementScanOrder() 和 selectLastInsertId() 落在同一个连接上
     * （LAST_INSERT_ID 是 session 级的，跨连接读到的是别人的号）。
     * <p>
     * 注意：这个方法必须由别的 Bean 调用（比如 Controller）。如果从本类的其他方法用
     * this.checkBox() 调，会绕过 Spring 代理，事务直接失效。
     */
    @Transactional(rollbackFor = Exception.class)
    public int checkBox(String orderNo, String boxNo) {

        DemoOrder demoOrder = demoOrderMapper.selectOne(
                new LambdaQueryWrapper<DemoOrder>().eq(DemoOrder::getOrderNo, orderNo));
        if (demoOrder == null) {
            throw new CheckException(CheckError.ORDER_NOT_FOUND);
        }

        DemoBox demoBox = demoBoxMapper.selectOne(
                new LambdaQueryWrapper<DemoBox>()
                        .eq(DemoBox::getOrderId, demoOrder.getId())
                        .eq(DemoBox::getBoxNo, boxNo));
        if (demoBox == null) {
            throw new CheckException(CheckError.BOX_NOT_FOUND);
        }

        // 顺序照抄生产的 boxVerification()：先抢箱，抢到了才分配序号。
        // 反过来（先拿号再抢箱）会在抢不到时把号烧掉，造成 scan_order 跳号。
        int affect=demoBoxMapper.tryLockBox(demoBox.getId());
        if (affect==0) {
            throw new CheckException(CheckError.ALREADY_CHECKED);
        }
        demoOrderMapper.incrementScanOrder(demoOrder.getId());
        int scanOrderId =demoOrderMapper.selectLastInsertId();
        demoBoxMapper.updateScanOrder(demoBox.getId(),scanOrderId);
        // 只是发布，不会立刻执行——SecondVerificationListener 等这个事务真正提交之后才处理，
        // 失败了也不影响这里已经拿到的核对结果。
        eventPublisher.publishEvent(new SecondVerificationEvent(demoOrder.getId(), demoBox.getId(), scanOrderId));
        return scanOrderId;
    }

    /**
     * 旧方案复刻，只用于压测对比（DemoOrderServiceLockContentionBenchmarkTest），
     * 不对外暴露 Controller 端点。
     * <p>
     * 跟 checkBox 唯一的区别：二次核对（demoSecondVerificationService.execute，~300ms）
     * 同步跑在同一个 @Transactional 方法里，不发事件、不走异步补偿。
     * <p>
     * 效果：incrementScanOrder() 对 demo_order 那一行加的 InnoDB 行锁，会一直持有到这
     * 300ms 跑完、事务提交（或异常回滚）才释放。期间任何并发线程想再对同一个订单
     * incrementScanOrder，都会被这把锁堵住排队——这就是生产优化前"同渠道并发扫码
     * 互相拖慢"的真实机制：N 个并发请求里最后一个的延迟 ≈ 前面几个的等待时间总和。
     * <p>
     * 这也是"故障隔离"那条优化点的反面教材：execute() 一旦抛异常（scanOrder 为 4 的
     * 倍数时必现），rollbackFor 会把这个事务里已经做的 CAS 抢箱、序号分配全部回滚掉——
     * 一个跟核心逻辑无关的下游超时，能把已经成功的核对结果也一起搭进去。
     */
    @Transactional(rollbackFor = Exception.class)
    public int checkBoxSync(String orderNo, String boxNo) {

        DemoOrder demoOrder = demoOrderMapper.selectOne(
                new LambdaQueryWrapper<DemoOrder>().eq(DemoOrder::getOrderNo, orderNo));
        if (demoOrder == null) {
            throw new CheckException(CheckError.ORDER_NOT_FOUND);
        }

        DemoBox demoBox = demoBoxMapper.selectOne(
                new LambdaQueryWrapper<DemoBox>()
                        .eq(DemoBox::getOrderId, demoOrder.getId())
                        .eq(DemoBox::getBoxNo, boxNo));
        if (demoBox == null) {
            throw new CheckException(CheckError.BOX_NOT_FOUND);
        }

        int affect = demoBoxMapper.tryLockBox(demoBox.getId());
        if (affect == 0) {
            throw new CheckException(CheckError.ALREADY_CHECKED);
        }
        demoOrderMapper.incrementScanOrder(demoOrder.getId());
        int scanOrderId = demoOrderMapper.selectLastInsertId();
        demoBoxMapper.updateScanOrder(demoBox.getId(), scanOrderId);

        // 旧方案：慢操作同步跑在事务里，事务提交前一直占着上面那行 UPDATE 加的行锁。
        demoSecondVerificationService.execute(demoOrder.getId(), demoBox.getId(), scanOrderId);

        return scanOrderId;
    }

}
