package com.chris64233.manufacturingrelease.domain;

/**
 * 批次审计事件类型，构成批次的完整版本历史。
 */
public enum EventType {
    BATCH_DECLARED,
    QUANTITY_UPDATED,
    RESULT_SUBMITTED,
    DEVIATION_OPENED,
    DEVIATION_DISPOSITIONED,
    DEVIATION_CLOSED,
    APPROVAL_GRANTED,
    APPROVAL_WITHDRAWN,
    RELEASED,
    RELEASE_REVOKED,
    SHIPPED
}
