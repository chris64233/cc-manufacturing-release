package com.chris64233.manufacturingrelease.domain;

/**
 * 偏差单状态。
 */
public enum DeviationStatus {
    /** 已登记，等待处置：不得放行 */
    OPEN,
    /** 已作出终态处理 */
    DECIDED
}
