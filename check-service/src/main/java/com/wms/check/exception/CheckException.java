package com.wms.check.exception;

import lombok.Getter;

/**
 * 继承 RuntimeException 而不是 Exception：Spring 的 @Transactional 默认只对
 * RuntimeException 和 Error 回滚，受检异常抛出去事务是会提交的。
 */
@Getter
public class CheckException extends RuntimeException {

    private final CheckError error;

    public CheckException(CheckError error) {
        super(error.getMessage());
        this.error = error;
    }
}
