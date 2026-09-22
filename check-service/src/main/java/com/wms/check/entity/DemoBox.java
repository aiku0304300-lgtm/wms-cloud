package com.wms.check.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 对应 demo_box 表：一行 = 一个具体要核对的箱子实例。
 * status: 0=未核对，1=已核对（CAS 的目标字段）。
 * scan_order: 核对成功后，回填 demo_order 计数器分配出来的序号，方便查看效果。
 * 同样只是字段搬运，查候选/CAS 的 SQL 不在这个类里。
 * get/set/toString 由 Lombok 的 @Data 在编译期自动生成。
 */
@Data
@TableName("demo_box")
public class DemoBox {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private String boxNo;

    private Integer status;

    private Integer scanOrder;

    /**
     * 由数据库列定义里的 ON UPDATE CURRENT_TIMESTAMP 自动维护，Java 这边不用手动赋值。
     */
    private LocalDateTime updateTime;
}
