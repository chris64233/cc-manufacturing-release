package com.chris64233.manufacturingrelease.domain;

/**
 * 偏差处置决定。
 *
 * <p>放行语义（仅在偏差终态 {@link DeviationStatus#CLOSED} 时生效）：
 * <ul>
 *     <li>{@link #CONDITIONAL_ACCEPT}：有条件接受，允许批次放行；</li>
 *     <li>{@link #REWORK}：返工，必须重新检验并合格后才允许放行；</li>
 *     <li>{@link #SCRAP}：报废，批次永久不可放行。</li>
 * </ul>
 */
public enum DispositionDecision {
    REWORK(false),
    SCRAP(false),
    CONDITIONAL_ACCEPT(true);

    private final boolean releaseAllowed;

    DispositionDecision(boolean releaseAllowed) {
        this.releaseAllowed = releaseAllowed;
    }

    /** 该处置本身（无需复验）是否允许放行。 */
    public boolean isReleaseAllowed() {
        return releaseAllowed;
    }
}
