package com.m78.netdisk.share.domain.enums;

public enum ShareExpire {
    ONE_DAY(24L, "一天"),
    ONE_WEEK(168L, "一周"),
    ONE_MONTH(720L, "一个月"),
    PERMANENT(null, "永久");

    private final Long hours;
    private final String label;

    ShareExpire(Long hours, String label) {
        this.hours = hours;
        this.label = label;
    }

    public Long getHours() { return hours; }
    public String getLabel() { return label; }

    public static ShareExpire fromType(String type) {
        if (type == null || type.isBlank()) return PERMANENT;
        for (ShareExpire e : values()) {
            if (e.name().equalsIgnoreCase(type)) return e;
        }
        return PERMANENT;
    }
}
