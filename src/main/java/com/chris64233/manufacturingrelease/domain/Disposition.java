package com.chris64233.manufacturingrelease.domain;

/**
 * 偏差终态处理方式。只有处置结论允许放行的偏差才不阻塞批次放行。
 */
public enum Disposition {
    /** 返工：返工后需要重新检验，本偏差允许被放行依据引用 */
    REWORK(true),
    /** 报废：该部分/批次判废，不允许放行 */
    SCRAP(false),
    /** 有条件接受：允许放行 */
    CONDITIONAL_ACCEPTANCE(true);

    private final boolean allowsRelease;

    Disposition(boolean allowsRelease) {
        this.allowsRelease = allowsRelease;
    }

    public boolean allowsRelease() {
        return allowsRelease;
    }
}
