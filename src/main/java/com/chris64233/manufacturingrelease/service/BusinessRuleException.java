package com.chris64233.manufacturingrelease.service;

/**
 * 业务规则冲突（状态冲突、重复提交、放行依据不完整等），映射 HTTP 409。
 */
public class BusinessRuleException extends RuntimeException {
    public BusinessRuleException(String message) {
        super(message);
    }
}
