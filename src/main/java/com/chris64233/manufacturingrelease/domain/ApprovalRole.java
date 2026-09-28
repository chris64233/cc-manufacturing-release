package com.chris64233.manufacturingrelease.domain;

/**
 * 质量审批角色。最终放行要求两个不同角色的有效审批。
 */
public enum ApprovalRole {
    /** 质量审批（如 QA 经理） */
    QUALITY_MANAGER,
    /** 生产审批（如生产经理） */
    PRODUCTION_MANAGER
}
