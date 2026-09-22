package com.wms.check.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 对应 demo_order 表：只做一件事——存这个订单自己的扫码计数器（last_scan_order）。
 * 这里只是纯字段搬运，没有任何业务逻辑，原子递增的 SQL 写在 Mapper 里，不在这个类里。
 * get/set/toString 由 Lombok 的 @Data 在编译期自动生成。
 */
@Data
@TableName("demo_order")
public class DemoOrder {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;

    private Integer lastScanOrder;
}
