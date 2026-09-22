package com.wms.check.exception;

import lombok.Getter;

/**
 * 核对失败的原因。对应 PHP 生产系统里统一 code=201、按原因给不同中文 msg 的做法。
 * 三种都不该重试：订单/箱号不存在是数据问题，重复核对是别人已经扫过了。
 */
@Getter
public enum CheckError {

    ORDER_NOT_FOUND(201, "未找到此订单数据，请重试"),
    BOX_NOT_FOUND(201, "未找到此箱号数据，请重试"),
    ALREADY_CHECKED(201, "重复核对");

    private final int code;
    private final String message;

    CheckError(int code, String message) {
        this.code = code;
        this.message = message;
    }
}
