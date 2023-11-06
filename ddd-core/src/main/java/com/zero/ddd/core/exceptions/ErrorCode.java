package com.zero.ddd.core.exceptions;


/**
 * 错误编码接口
 * 业务系统实现错误枚举的时候需要实现此接口。
 */
public interface ErrorCode {
    int getCode();
    String getMessage();
    String getDesc();
}
