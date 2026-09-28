package com.chris64233.manufacturingrelease.service;

/**
 * 引用对象不存在（批次/检验项目/偏差等），映射 HTTP 404。
 */
public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
