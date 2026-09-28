package com.chris64233.manufacturingrelease.domain;

/**
 * 偏差单状态。
 *
 * <p>流转：{@link #OPEN} → {@link #IN_DISPOSITION}（返工过程中）/ {@link #CLOSED}。
 * 只有 {@link #CLOSED} 才是允许参与放行判定的终态。
 */
public enum DeviationStatus {
    /** 已登记，尚未给出处置。 */
    OPEN,
    /** 已决定返工，等待重新检验合格。 */
    IN_DISPOSITION,
    /** 终态：处置完成（有条件接受 / 报废关闭 / 返工后复验合格关闭）。 */
    CLOSED
}
