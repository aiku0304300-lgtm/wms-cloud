package com.wms.check.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.check.entity.DemoBox;
import com.wms.check.entity.DemoOrder;
import com.wms.check.exception.CheckError;
import com.wms.check.exception.CheckException;
import com.wms.check.mapper.DemoBoxMapper;
import com.wms.check.mapper.DemoOrderMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 打真实 MySQL 的并发集成测试，不 mock。
 * 每次跑用随机 orderNo 造自己的数据，跑完删掉，不碰你库里原有的测试数据。
 */
@SpringBootTest
class DemoOrderServiceConcurrencyTest {

    private static final int THREADS = 20;

    @Autowired
    private DemoOrderService demoOrderService;

    @Autowired
    private DemoOrderMapper demoOrderMapper;

    @Autowired
    private DemoBoxMapper demoBoxMapper;

    private String orderNo;
    private Long orderId;

    @BeforeEach
    void setUp() {
        orderNo = "IT-" + UUID.randomUUID().toString().substring(0, 8);
        DemoOrder order = new DemoOrder();
        order.setOrderNo(orderNo);
        order.setLastScanOrder(0);
        demoOrderMapper.insert(order);
        orderId = order.getId();
    }

    @AfterEach
    void tearDown() {
        demoBoxMapper.delete(new LambdaQueryWrapper<DemoBox>().eq(DemoBox::getOrderId, orderId));
        demoOrderMapper.deleteById(orderId);
    }

    private void insertBox(String boxNo) {
        DemoBox box = new DemoBox();
        box.setOrderId(orderId);
        box.setBoxNo(boxNo);
        box.setStatus(0);
        demoBoxMapper.insert(box);
    }

    @Test
    @DisplayName("同一个箱号被并发扫多次，只能有一个成功，其余都是重复核对")
    void sameBoxOnlyOneWins() throws Exception {
        insertBox("BOX-1");

        AtomicInteger success = new AtomicInteger();
        AtomicInteger duplicated = new AtomicInteger();
        List<Throwable> unexpected = new ArrayList<>();

        runConcurrently(i -> {
            try {
                demoOrderService.checkBox(orderNo, "BOX-1");
                success.incrementAndGet();
            } catch (CheckException e) {
                if (e.getError() == CheckError.ALREADY_CHECKED) {
                    duplicated.incrementAndGet();
                } else {
                    synchronized (unexpected) { unexpected.add(e); }
                }
            } catch (Throwable t) {
                synchronized (unexpected) { unexpected.add(t); }
            }
        });

        assertTrue(unexpected.isEmpty(), () -> "出现了预期外的异常: " + unexpected);
        assertEquals(1, success.get(), "CAS 失效了：同一个箱子被核对成功了多次");
        assertEquals(THREADS - 1, duplicated.get());
    }

    @Test
    @DisplayName("并发扫不同箱号，序号必须唯一且连续，不能重号也不能跳号")
    void scanOrderIsUniqueAndContiguous() throws Exception {
        for (int i = 1; i <= THREADS; i++) {
            insertBox("BOX-" + i);
        }

        List<Throwable> unexpected = new ArrayList<>();

        runConcurrently(i -> {
            try {
                demoOrderService.checkBox(orderNo, "BOX-" + (i + 1));
            } catch (Throwable t) {
                synchronized (unexpected) { unexpected.add(t); }
            }
        });

        assertTrue(unexpected.isEmpty(), () -> "出现了预期外的异常: " + unexpected);

        List<Integer> scanOrders = demoBoxMapper
                .selectList(new LambdaQueryWrapper<DemoBox>().eq(DemoBox::getOrderId, orderId))
                .stream()
                .map(DemoBox::getScanOrder)
                .sorted()
                .toList();

        List<Integer> expected = new ArrayList<>();
        for (int i = 1; i <= THREADS; i++) {
            expected.add(i);
        }
        // 相等即同时证明了三件事：没有 null（都回填了）、没有重号（LAST_INSERT_ID 读的是自己连接的值）、没有跳号
        assertEquals(expected, scanOrders, "序号出现重号或跳号");

        DemoOrder order = demoOrderMapper.selectById(orderId);
        assertEquals(THREADS, order.getLastScanOrder(), "计数器终值和成功次数对不上");
    }

    private void runConcurrently(java.util.function.IntConsumer task) throws Exception {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            final int idx = i;
            pool.submit(() -> {
                try {
                    startGate.await();
                    task.accept(idx);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        assertTrue(endGate.await(30, TimeUnit.SECONDS), "并发任务超时没跑完");
        pool.shutdown();
    }
}
