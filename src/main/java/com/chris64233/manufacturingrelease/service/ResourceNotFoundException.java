package com.chris64233.manufacturingrelease.service;

/**
 * 请求引用的业务对象（批次、检验项目、偏差、审批等）不存在。
 */
public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }
}
