-- 手动建库建表，不走 Flyway/Liquibase（演示项目，图简单）。
-- 建库：CREATE DATABASE wms_check DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS demo_order (
    id               BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_no         VARCHAR(64) NOT NULL COMMENT '订单号',
    last_scan_order  INT NOT NULL DEFAULT 0 COMMENT '扫描序号计数器，原子递增靠 LAST_INSERT_ID(expr)',
    UNIQUE KEY uk_order_no (order_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表（demo，只保留复刻并发优化必需的字段）';

CREATE TABLE IF NOT EXISTS demo_box (
    id           BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id     BIGINT UNSIGNED NOT NULL COMMENT '所属订单 id',
    box_no       VARCHAR(64) NOT NULL COMMENT '箱号，扫码枪给的入参',
    status       TINYINT NOT NULL DEFAULT 0 COMMENT '0=未核对 1=已核对，CAS 的目标字段',
    scan_order   INT NULL COMMENT '核对成功后回填的扫描序号',
    update_time  TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_order_id (order_id),
    UNIQUE KEY uk_order_box (order_id, box_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='箱子表（demo），CAS 抢占发生在 status 字段上';

CREATE TABLE IF NOT EXISTS demo_second_verification_log (
    id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    order_id      BIGINT UNSIGNED NOT NULL,
    box_id        BIGINT UNSIGNED NOT NULL,
    scan_order    INT NULL,
    error_msg     VARCHAR(512) NULL,
    retry_status  TINYINT NOT NULL DEFAULT 0 COMMENT '0=待补偿 1=补偿成功 2=重试到上限仍失败',
    retry_count   INT NOT NULL DEFAULT 0,
    create_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    KEY idx_retry_status (retry_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步二次核对失败留痕表，定时任务靠 retry_status 找待补偿记录';
