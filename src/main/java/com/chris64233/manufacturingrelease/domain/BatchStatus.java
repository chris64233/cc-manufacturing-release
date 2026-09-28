package com.chris64233.manufacturingrelease.domain;

/**
 * 批次生命周期状态。
 *
 * <p>流转：{@link #CREATED} → {@link #RELEASED} → {@link #SHIPPED}；
 * 已放行/已出库批次发现错误时登记撤销事件，进入 {@link #REVOKED}。
 */
public enum BatchStatus {
    /** 已声明，检验、偏差、审批进行中，尚未放行。 */
    CREATED,
    /** 已放行，数量与放行依据锁定。 */
    RELEASED,
    /** 已放行且已出库。 */
    SHIPPED,
    /** 已登记撤销放行事件；未出库时阻止继续流转。 */
    REVOKED
}
