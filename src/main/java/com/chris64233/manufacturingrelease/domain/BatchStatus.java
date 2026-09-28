package com.chris64233.manufacturingrelease.domain;

/**
 * 批次生命周期状态。
 */
public enum BatchStatus {
    /** 检验/偏差/审批进行中，尚未放行 */
    PENDING_RELEASE,
    /** 已放行，数量与放行依据锁定不可修改 */
    RELEASED,
    /** 放行已被撤销，批次不得继续流转（如出库） */
    RELEASE_REVOKED
}
