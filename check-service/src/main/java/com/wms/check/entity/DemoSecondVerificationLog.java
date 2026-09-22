package com.wms.check.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 核对成功后的附属慢操作（模拟生产的 createSecondVerification）失败记录。
 * 只有失败才会有一行——成功的话事件处理完直接结束，什么都不落库。
 * retry_status: 0=待补偿 1=补偿成功 2=重试到上限仍失败。
 */
@Data
@TableName("demo_second_verification_log")
public class DemoSecondVerificationLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;

    private Long boxId;

    private Integer scanOrder;

    private String errorMsg;

    private Integer retryStatus;

    private Integer retryCount;

    private LocalDateTime createTime;

    private LocalDateTime updateTime;
}
