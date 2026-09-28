package com.chris64233.manufacturingrelease.service;

/**
 * 业务规则冲突（状态非法、依据不完整、不可变对象被修改、重复业务号等），对应 HTTP 409。
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
