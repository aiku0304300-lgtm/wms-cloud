package com.wms.check.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.wms.check.entity.DemoBox;
import com.wms.check.entity.DemoOrder;
import com.wms.check.mapper.DemoBoxMapper;
import com.wms.check.mapper.DemoOrderMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Arrays;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.BiFunction;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 不是正确性测试，是延迟压测：复现生产那次真实优化——"慢操作（二次核对，~300ms）
 * 放在事务内同步执行" vs "挪到事务外异步执行"，对同一个订单并发扫码的延迟分布有多大影响。
 * <p>
 * 机制不是模拟出来的，是真实的 InnoDB 行锁：incrementScanOrder() 对 demo_order 那一行
 * 加的行锁，在 checkBoxSync（旧方案）里会一直持有到 300ms 慢操作跑完、事务提交才释放；
 * 在 checkBox（新方案）里只持有到那条 UPDATE 语句本身提交，微秒级。并发线程排队等同一把
 * 锁时，最后一个线程的延迟 ≈ 前面几个线程的等待时间总和——这就是 1261ms 这种数字的来源，
 * 不是靠单纯"CAS 比查改写快"能解释的。
 * <p>
 * 每轮并发数 THREADS=5，对应生产"同渠道当天前几次并发扫码"那种量级，旧方案预期
 * ≈ THREADS × 300ms 量级堆叠，新方案预期是毫秒级、基本不随并发数堆叠。
 */
@SpringBootTest
class DemoOrderServiceLockContentionBenchmarkTest {

    private static final int THREADS = 5;

    @Autowired
    private DemoOrderService demoOrderService;

    @Autowired
    private DemoOrderMapper demoOrderMapper;

    @Autowired
    private DemoBoxMapper demoBoxMapper;

    @Test
    void compareLockHoldTimeImpact() throws Exception {
        long[] oldLatenciesMs = runBatch(demoOrderService::checkBoxSync);
        long[] newLatenciesMs = runBatch(demoOrderService::checkBox);

        printStats("旧方案（二次核对同步在事务内，持锁 ~300ms）", oldLatenciesMs);
        printStats("新方案（二次核对事务外异步，持锁微秒级）", newLatenciesMs);

        double oldAvg = average(oldLatenciesMs);
        double newAvg = average(newLatenciesMs);
        System.out.printf("倍数差异：旧方案平均耗时是新方案的 %.1f 倍%n", oldAvg / newAvg);

        assertTrue(newAvg < oldAvg, "预期新方案（事务外异步）平均延迟应明显低于旧方案");
    }

    /**
     * 造一个全新订单 + THREADS 个空箱子，THREADS 个线程同时扫（各扫各的箱号，
     * 只在 demo_order 这一行的行锁上产生竞争），记录每个线程自己那次调用的耗时。
     */
    private long[] runBatch(BiFunction<String, String, Integer> checkFn) throws InterruptedException {
        String orderNo = "BENCH-" + UUID.randomUUID().toString().substring(0, 8);
        DemoOrder order = new DemoOrder();
        order.setOrderNo(orderNo);
        order.setLastScanOrder(0);
        demoOrderMapper.insert(order);
        Long orderId = order.getId();

        for (int i = 1; i <= THREADS; i++) {
            DemoBox box = new DemoBox();
            box.setOrderId(orderId);
            box.setBoxNo("BOX-" + i);
            box.setStatus(0);
            demoBoxMapper.insert(box);
        }

        long[] latenciesMs = new long[THREADS];
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(THREADS);

        for (int i = 0; i < THREADS; i++) {
            final int idx = i;
            final String boxNo = "BOX-" + (idx + 1);
            pool.submit(() -> {
                try {
                    startGate.await();
                    long start = System.nanoTime();
                    try {
                        checkFn.apply(orderNo, boxNo);
                    } catch (Exception ignored) {
                        // 演示用的模拟失败（scanOrder 为 4 的倍数）也要计入延迟——
                        // 失败照样占了行锁到 300ms 结束才回滚，这正是要展示的问题之一。
                    }
                    latenciesMs[idx] = (System.nanoTime() - start) / 1_000_000;
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

        demoBoxMapper.delete(new LambdaQueryWrapper<DemoBox>().eq(DemoBox::getOrderId, orderId));
        demoOrderMapper.deleteById(orderId);

        return latenciesMs;
    }

    private void printStats(String label, long[] latenciesMs) {
        long[] sorted = latenciesMs.clone();
        Arrays.sort(sorted);
        double avg = average(latenciesMs);
        long p50 = sorted[sorted.length / 2];
        long p95 = sorted[(int) Math.ceil(sorted.length * 0.95) - 1];
        long max = sorted[sorted.length - 1];
        System.out.printf("[%s] 样本数=%d  avg=%.1fms  p50=%dms  p95=%dms  max=%dms  明细=%s%n",
                label, sorted.length, avg, p50, p95, max, Arrays.toString(latenciesMs));
    }

    private double average(long[] arr) {
        long sum = 0;
        for (long v : arr) {
            sum += v;
        }
        return (double) sum / arr.length;
    }
}
