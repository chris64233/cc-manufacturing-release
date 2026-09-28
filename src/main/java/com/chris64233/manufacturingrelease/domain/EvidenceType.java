package com.chris64233.manufacturingrelease.domain;

/**
 * 放行证据快照条目类型。
 */
public enum EvidenceType {
    /** 放行时锁定的检验结果版本 */
    INSPECTION_RESULT,
    /** 放行时锁定的偏差终态处理 */
    DEVIATION,
    /** 放行时锁定的有效审批 */
    APPROVAL
}
