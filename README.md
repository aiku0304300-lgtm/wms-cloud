# wms-cloud

一个 Spring Cloud Alibaba 微服务练手项目，同时也是我对一次**真实生产环境并发优化案例**的复刻与验证：出库扫码核对场景下，把"查-改-写"三步式的核对逻辑改造成 CAS + 数据库原子自增，并把非核心的慢操作移出事务、走异步补偿——线上实测把并发场景下的响应延迟从 **1261ms** 降到了 **44.8ms**。

本仓库里的 `check-service` 用同样的机制（真实 InnoDB 行锁，不是模拟）复现了这个数量级的差异，见下方"压测结果"。

## 技术栈

- Spring Boot 3.2 / Spring Cloud 2023.0.3 / Spring Cloud Alibaba 2023.0.3
- Nacos（服务注册发现）、Sentinel（限流）、Spring Cloud Gateway
- MyBatis-Plus 3.5 + MySQL 8 / InnoDB
- JUnit 5 + 真实 MySQL 的并发集成测试（不 mock）

## 模块结构

```
wms-cloud
├── gateway            网关，统一入口，路由到各业务服务
├── inventory-service   库存查询服务（被 Feign 调用，纯内部服务）
└── check-service        核心：出库扫码核对，本项目的重点复刻对象
```

## 背景：原方案的问题

原 PHP 生产系统的核对逻辑是典型的"查-改-写"三步：

1. `SELECT` 查当前扫描计数器 / 箱子状态
2. 应用层判断箱子是否已核对
3. `UPDATE` 写回新状态、新序号

这三步不是原子的。高并发扫码时，两个线程可能在第 1 步都读到"未核对"，于是都在第 3 步写成功——同一个箱子被核对两次；并且序号分配也可能因为读写间隙产生重号或跳号。

## 新方案

### 1. CAS 抢占箱子

把"检查"塞进 `UPDATE` 语句自己的 `WHERE` 里，让"读旧值"和"写新值"在数据库层面变成不可分割的一步：

```sql
UPDATE demo_box SET status = 1 WHERE id = ? AND status = 0
```

InnoDB 的行锁保证同一时刻只有一个事务能让这条语句生效。谁的 `affected rows = 1` 谁就是抢到的一方，`affected rows = 0` 就说明已经被别人抢了，直接判失败，不重试（重试没有意义——箱子确实已经被核对过）。

### 2. 原子分配序号

用 `LAST_INSERT_ID(expr)` 让"计数器自增"和"读回分配到的值"都在同一条 SQL / 同一个数据库连接上完成，不需要应用层加锁：

```sql
UPDATE demo_order SET last_scan_order = LAST_INSERT_ID(last_scan_order + 1) WHERE id = ?
SELECT LAST_INSERT_ID()
```

`LAST_INSERT_ID()` 是 **session 级**的，必须保证这两条语句落在同一个数据库连接上——这里靠 `@Transactional` 把连接绑定到当前线程来实现，所以这个方法必须经过 Spring 代理调用（不能在同一个类里 `this.xxx()` 自调用，否则事务直接失效）。

### 3. 慢操作移出事务，异步补偿

核对成功后还有一个跟核心逻辑无关但耗时（~300ms）的下游附属操作（生产里是二次核对数据落库）。新方案把它放到事务提交之后异步执行：

```java
@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
@Async
public void onSecondVerification(SecondVerificationEvent event) { ... }
```

- 失败会落一条待补偿记录，由 `@Scheduled` 定时任务重试（最多 3 次，超过上限留痕放弃），这是"最终一致性"的落地方式。
- 这个改动带来两个收益：**延迟**（用户不用等这 300ms）和**故障隔离**（下游超时不会拖累已经成功提交的核对结果——旧方案里 `rollbackFor` 会把 CAS 抢箱、序号分配一起回滚掉）。故障隔离是更重要的一点，因为慢操作失败是低频事件，延迟收益反而是长期高频存在的。

## 压测结果

`DemoOrderServiceLockContentionBenchmarkTest` 用真实 InnoDB 行锁复现两种方案在同一个订单上并发扫码（5 线程）的延迟分布，不是模拟出来的：`incrementScanOrder()` 对 `demo_order` 那一行加的行锁，在旧方案里会一直持有到 300ms 的慢操作跑完、事务提交才释放；新方案里只持有到那条 `UPDATE` 语句本身提交，微秒级。

| 方案 | avg | p50 | p95 | max |
|---|---|---|---|---|
| 旧：慢操作同步在事务内 | 968.6ms | 970ms | 1589ms | 1589ms |
| 新：慢操作移出事务异步执行 | 44.6ms | 44ms | 46ms | 46ms |

新方案平均延迟约为旧方案的 **1/21.7**，与生产实测的 1261ms → 44.8ms 同一数量级。

## 本地运行

### 1. 建库建表

```sql
CREATE DATABASE wms_check DEFAULT CHARACTER SET utf8mb4;
```

再执行 `check-service/src/main/resources/schema.sql` 建表。

### 2. 数据库账号密码

默认读 `root/root`（本地开发占位符），可用环境变量覆盖：

```
DB_USERNAME=xxx
DB_PASSWORD=xxx
```

### 3. 启动顺序

Nacos → check-service（含 inventory-service、gateway 视需要）：

```bash
# check-service 需要在自己目录下跑，不要从根目录带 -pl -am 跑 spring-boot:run
cd check-service
mvn spring-boot:run
```

### 4. 试一下核对接口

```bash
curl -X POST "http://localhost:8081/check/box?orderNo=xxx&boxNo=xxx"
```

### 5. 跑测试

```bash
# 正确性：并发下 CAS 唯一成功、序号不重不跳
mvn -pl check-service -am test -Dtest=DemoOrderServiceConcurrencyTest

# 压测：复现真实生产的延迟对比
mvn -pl check-service -am test -Dtest=DemoOrderServiceLockContentionBenchmarkTest
```
