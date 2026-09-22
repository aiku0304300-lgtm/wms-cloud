package com.wms.check.event;

import lombok.Getter;

/**
 * "核对成功了，该去触发附属的二次核对了"这件事本身。
 * 是普通 POJO，不用继承 ApplicationEvent——Spring 4.2 起 ApplicationEventPublisher
 * 可以直接发布任意对象当事件，监听方按类型订阅即可。
 */
@Getter
public class SecondVerificationEvent {

    private final Long orderId;
    private final Long boxId;
    private final Integer scanOrder;

    public SecondVerificationEvent(Long orderId, Long boxId, Integer scanOrder) {
        this.orderId = orderId;
        this.boxId = boxId;
        this.scanOrder = scanOrder;
    }
}
