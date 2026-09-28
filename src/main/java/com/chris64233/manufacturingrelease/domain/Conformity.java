package com.chris64233.manufacturingrelease.domain;

/**
 * 检验结果的合格判定。
 */
public enum Conformity {
    /** 合格 */
    CONFORMING,
    /** 不合格：必须关联偏差单 */
    NONCONFORMING
}
