package com.m78.netdisk.calendar.util;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * 农历公历转换工具
 * 支持 1901-2100 年范围的农历计算
 */
public class LunarCalendarUtil {

    public static class LunarDate {
        public final int lunarYear;
        public final int lunarMonth;
        public final int lunarDay;
        public final boolean isLeap;
        public final String heavenlyStem;
        public final String earthlyBranch;
        public final String zodiac;
        public final String monthStem;
        public final String monthBranch;
        public final String dayStem;
        public final String dayBranch;
        public final String jieQi;
        public final String monthName;
        public final String dayName;
        public final String jianChu;

        public LunarDate(int lunarYear, int lunarMonth, int lunarDay, boolean isLeap,
                         String heavenlyStem, String earthlyBranch, String zodiac,
                         String monthStem, String monthBranch,
                         String dayStem, String dayBranch,
                         String jieQi, String monthName, String dayName, String jianChu) {
            this.lunarYear = lunarYear;
            this.lunarMonth = lunarMonth;
            this.lunarDay = lunarDay;
            this.isLeap = isLeap;
            this.heavenlyStem = heavenlyStem;
            this.earthlyBranch = earthlyBranch;
            this.zodiac = zodiac;
            this.monthStem = monthStem;
            this.monthBranch = monthBranch;
            this.dayStem = dayStem;
            this.dayBranch = dayBranch;
            this.jieQi = jieQi;
            this.monthName = monthName;
            this.dayName = dayName;
            this.jianChu = jianChu;
        }
    }

    // 基准：1901年1月1日 = 农历庚子年十一月十一
    private static final LocalDate BASE_GREGORIAN = LocalDate.of(1901, 1, 1);
    private static final int BASE_LUNAR_YEAR = 1900;
    private static final int BASE_LUNAR_MONTH = 11;
    private static final int BASE_LUNAR_DAY = 11;
    // 天干地支基准：1900年（庚子年）
    private static final int BASE_GAN_ZHI_YEAR = 1900;

    /**
     * 公历转农历
     */
    public static LunarDate solarToLunar(int year, int month, int day) {
        LocalDate solarDate = LocalDate.of(year, month, day);
        long offset = ChronoUnit.DAYS.between(BASE_GREGORIAN, solarDate);
        if (offset < 0) {
            throw new IllegalArgumentException("仅支持1901年之后的日期");
        }

        // 查找农历年份
        int lunarYear = BASE_LUNAR_YEAR;
        int yearDays = 0;
        int i = BASE_LUNAR_YEAR - 1900;

        while (i < LunarCalendarData.LUNAR_INFO.length) {
            yearDays = lunarYearDays(1900 + i);
            if (offset < yearDays) break;
            offset -= yearDays;
            i++;
            lunarYear++;
        }

        if (i >= LunarCalendarData.LUNAR_INFO.length) {
            throw new IllegalArgumentException("超出支持的年范围");
        }

        // 查找农历月
        int leapMonth = leapMonth(1900 + i);
        boolean isLeap = false;
        int lunarMonth;
        int monthDays;

        for (lunarMonth = 1; lunarMonth <= 12; lunarMonth++) {
            monthDays = lunarMonthDays(1900 + i, lunarMonth);
            if (leapMonth > 0 && lunarMonth == leapMonth + 1) {
                // 闰月
                if (isLeap) {
                    // 已经在闰月之后
                } else {
                    // 先处理闰月
                    int leapDays = leapMonthDays(1900 + i);
                    if (offset < leapDays) {
                        isLeap = true;
                        lunarMonth--; // 表示这个月是闰月
                        break;
                    }
                    offset -= leapDays;
                    isLeap = true; // 标记已经越过闰月
                }
            }
            if (offset < monthDays) break;
            offset -= monthDays;
        }

        if (lunarMonth > 12) {
            lunarMonth = 12;
        }

        int lunarDay = (int) offset + 1;

        // 计算天干地支
        int yearGanZhiIndex = (lunarYear - BASE_GAN_ZHI_YEAR) % 60;
        if (yearGanZhiIndex < 0) yearGanZhiIndex += 60;
        String yearStem = LunarCalendarData.TIAN_GAN[yearGanZhiIndex % 10];
        String yearBranch = LunarCalendarData.DI_ZHI[yearGanZhiIndex % 12];
        String zodiac = LunarCalendarData.ZODIAC[yearGanZhiIndex % 12];

        // 月干支：以节气为界，按农历月计算
        int monthGanZhiBase = ((lunarYear - BASE_GAN_ZHI_YEAR) * 12 + lunarMonth) % 60;
        if (monthGanZhiBase < 0) monthGanZhiBase += 60;
        String monthStem = LunarCalendarData.TIAN_GAN[monthGanZhiBase % 10];
        String monthBranch = LunarCalendarData.DI_ZHI[monthGanZhiBase % 12];

        // 日干支（从基准日推算）
        long ganZhiOffset = ChronoUnit.DAYS.between(LocalDate.of(1900, 1, 1), solarDate) % 60;
        if (ganZhiOffset < 0) ganZhiOffset += 60;
        int dayStemIndex = (int) ((ganZhiOffset + 10) % 10); // 1900-01-01 为甲子日，甲=0
        int dayBranchIndex = (int) (ganZhiOffset % 12);
        String dayStem = LunarCalendarData.TIAN_GAN[dayStemIndex];
        String dayBranch = LunarCalendarData.DI_ZHI[dayBranchIndex];

        // 节气
        String jieQi = getSolarTermDescription(year, month, day);

        // 农历月名称
        String monthName;
        if (isLeap && lunarMonth <= 12) {
            monthName = "\u95f0" + LunarCalendarData.LUNAR_MONTH_NAMES[lunarMonth - 1];
        } else if (lunarMonth >= 1 && lunarMonth <= 12) {
            monthName = LunarCalendarData.LUNAR_MONTH_NAMES[lunarMonth - 1];
        } else {
            monthName = "\u5341\u4e00\u6708"; // fallback
        }

        // 农历日名称
        String dayName = lunarDay >= 1 && lunarDay <= 30
                ? LunarCalendarData.LUNAR_DAY_NAMES[lunarDay - 1] : "\u521d\u4e00";

        // 建除
        String jianChu = getJianChu(lunarMonth, lunarDay);

        return new LunarDate(lunarYear, lunarMonth, lunarDay, isLeap,
                yearStem, yearBranch, zodiac,
                monthStem, monthBranch,
                dayStem, dayBranch,
                jieQi, monthName, dayName, jianChu);
    }

    /**
     * 获取当前日期的农历
     */
    public static LunarDate today() {
        LocalDate now = LocalDate.now();
        return solarToLunar(now.getYear(), now.getMonthValue(), now.getDayOfMonth());
    }

    // ==================== 农历计算 ====================

    private static int lunarYearDays(int year) {
        int i = year - 1900;
        if (i < 0 || i >= LunarCalendarData.LUNAR_INFO.length) return 365;
        int info = LunarCalendarData.LUNAR_INFO[i];
        int sum = 29 * 12;
        for (int j = 0x8000; j >= 0x8; j >>= 1) {
            sum += (info & j) > 0 ? 1 : 0;
        }
        int leap = leapMonth(year);
        if (leap > 0) {
            sum += leapMonthDays(year);
        }
        return sum;
    }

    private static int leapMonth(int year) {
        int i = year - 1900;
        if (i < 0 || i >= LunarCalendarData.LUNAR_INFO.length) return 0;
        return LunarCalendarData.LUNAR_INFO[i] & 0xf;
    }

    private static int leapMonthDays(int year) {
        int i = year - 1900;
        if (i < 0 || i >= LunarCalendarData.LUNAR_INFO.length) return 29;
        return (LunarCalendarData.LUNAR_INFO[i] & 0x10000) > 0 ? 30 : 29;
    }

    private static int lunarMonthDays(int year, int month) {
        int i = year - 1900;
        if (i < 0 || i >= LunarCalendarData.LUNAR_INFO.length) return 29;
        return (LunarCalendarData.LUNAR_INFO[i] & (0x10000 >> month)) > 0 ? 30 : 29;
    }

    // ==================== 节气 ====================

    private static String getSolarTermDescription(int year, int month, int day) {
        // 根据公历月份+日期判断处于哪个节气区间（简化版）
        double[] baseJieQi = new double[]{
                5.4055, 20.12, 3.87, 18.74, 5.63, 20.646, 4.81, 20.1, 5.52, 21.04,
                5.678, 21.37, 7.108, 22.83, 7.5, 23.13, 7.646, 23.042, 8.318, 23.438,
                7.438, 22.36, 7.18, 21.94, 5.4055
        };

        String prevJieQi = "";
        String nextJieQi = "";
        int prevDay = 0;

        for (int i = 0; i < 24; i++) {
            int jieQiMonth = i / 2 + 1;
            int jieQiDay = (int) Math.round(baseJieQi[i]);

            if (jieQiMonth < month || (jieQiMonth == month && jieQiDay <= day)) {
                prevJieQi = LunarCalendarData.JIE_QI[i];
                prevDay = jieQiDay;
            }
            if (jieQiMonth > month || (jieQiMonth == month && jieQiDay > day)) {
                nextJieQi = LunarCalendarData.JIE_QI[i];
                break;
            }
        }

        if (prevJieQi.isEmpty() && nextJieQi.isEmpty()) {
            return "";
        }

        int daysSincePrev = day - prevDay;
        if (nextJieQi.isEmpty()) {
            return prevJieQi + "\u540e" + daysSincePrev + "\u5929";
        }

        // 判断更接近哪个节气
        int nextMonth = -1, nextDay = -1;
        for (int i = 0; i < 24; i++) {
            if (LunarCalendarData.JIE_QI[i].equals(nextJieQi)) {
                nextMonth = i / 2 + 1;
                nextDay = (int) Math.round(baseJieQi[i]);
                break;
            }
        }

        int daysToNext = (nextDay - day) + (nextMonth - month) * 30;
        if (daysSincePrev <= daysToNext) {
            return prevJieQi + "\u540e";
        } else {
            return nextJieQi + "\u524d";
        }
    }

    // ==================== 建除十二神 ====================

    /**
     * 根据农历月、日计算建除
     * 算法：正月寅、二月卯…月地支=(month+1)%12
     * 日地支与月地支的关系决定建除
     */
    public static String getJianChu(int lunarMonth, int lunarDay) {
        // 月地支：正月=寅(2)，二月=卯(3)...
        int monthDiZhi = (lunarMonth + 1) % 12; // 正月=2(寅)

        // 日地支
        int dayDiZhi = (lunarDay - 1) % 12;

        // 建除十二神：建除满平定执破危成收开闭
        String[] jianChuArray = {"\u5efa", "\u9664", "\u6ee1", "\u5e73", "\u5b9a", "\u6267",
                "\u7834", "\u5371", "\u6210", "\u6536", "\u5f00", "\u95ed"};

        // 日地支与月地支的差值决定是哪一个建除神
        int diff = (dayDiZhi - monthDiZhi + 12) % 12;
        return jianChuArray[diff];
    }

    // ==================== 宜忌映射 ====================

    private static final java.util.Map<String, java.util.List<String>> YI_MAP = new java.util.HashMap<>();
    private static final java.util.Map<String, java.util.List<String>> JI_MAP = new java.util.HashMap<>();

    static {
        YI_MAP.put("\u5efa", java.util.List.of("\u65b0\u5efa\u5206\u4eab", "\u4e0a\u4f20\u6587\u4ef6"));
        YI_MAP.put("\u9664", java.util.List.of("\u53d6\u6d88\u5206\u4eab", "\u6e05\u7406\u8fc7\u671f\u5206\u4eab"));
        YI_MAP.put("\u6ee1", java.util.List.of("\u4e0a\u4f20\u6587\u4ef6", "\u5b58\u50a8\u5907\u4efd"));
        YI_MAP.put("\u5e73", java.util.List.of("\u65e5\u5e38\u6d4f\u89c8", "\u7ba1\u7406\u5206\u4eab"));
        YI_MAP.put("\u5b9a", java.util.List.of("\u521b\u5efa\u957f\u671f\u5206\u4eab", "\u6c38\u4e45\u5206\u4eab"));
        YI_MAP.put("\u6267", java.util.List.of("\u6587\u4ef6\u5907\u4efd", "\u5b58\u6863"));
        YI_MAP.put("\u7834", java.util.List.of());
        YI_MAP.put("\u5371", java.util.List.of("\u68c0\u67e5\u5206\u4eab\u72b6\u6001"));
        YI_MAP.put("\u6210", java.util.List.of("\u65b0\u5efa\u5206\u4eab", "\u4e0a\u4f20\u6587\u4ef6"));
        YI_MAP.put("\u6536", java.util.List.of("\u6536\u53d6\u4ed6\u4eba\u5206\u4eab", "\u4e0b\u8f7d\u6587\u4ef6"));
        YI_MAP.put("\u5f00", java.util.List.of("\u65b0\u5efa\u5206\u4eab", "\u5f00\u542f\u65b0\u9879\u76ee"));
        YI_MAP.put("\u95ed", java.util.List.of("\u6587\u4ef6\u5f52\u6863", "\u5173\u95ed\u5206\u4eab"));

        JI_MAP.put("\u5efa", java.util.List.of());
        JI_MAP.put("\u9664", java.util.List.of("\u65b0\u5efa\u5206\u4eab", "\u4e0a\u4f20\u6587\u4ef6"));
        JI_MAP.put("\u6ee1", java.util.List.of("\u53d6\u6d88\u5206\u4eab"));
        JI_MAP.put("\u5e73", java.util.List.of("\u91cd\u5927\u64cd\u4f5c"));
        JI_MAP.put("\u5b9a", java.util.List.of("\u66f4\u6539\u5206\u4eab\u8bbe\u7f6e"));
        JI_MAP.put("\u6267", java.util.List.of("\u5220\u9664\u6587\u4ef6"));
        JI_MAP.put("\u7834", java.util.List.of("\u65b0\u5efa\u5206\u4eab", "\u4e0a\u4f20\u6587\u4ef6",
                "\u4e0b\u8f7d\u6587\u4ef6", "\u53d6\u6d88\u5206\u4eab"));
        JI_MAP.put("\u5371", java.util.List.of("\u4fee\u6539\u5206\u4eab", "\u65b0\u5efa\u5206\u4eab"));
        JI_MAP.put("\u6210", java.util.List.of("\u53d6\u6d88\u5206\u4eab"));
        JI_MAP.put("\u6536", java.util.List.of("\u53d6\u6d88\u5206\u4eab"));
        JI_MAP.put("\u5f00", java.util.List.of("\u5173\u95ed\u5206\u4eab"));
        JI_MAP.put("\u95ed", java.util.List.of("\u65b0\u5efa\u5206\u4eab", "\u4e0a\u4f20\u6587\u4ef6"));
    }

    public static java.util.List<String> getYi(String jianChu) {
        return YI_MAP.getOrDefault(jianChu, java.util.List.of());
    }

    public static java.util.List<String> getJi(String jianChu) {
        return JI_MAP.getOrDefault(jianChu, java.util.List.of());
    }

    /**
     * 获取分享相关宜忌建议
     */
    public static ShareAdvice getShareAdvice(String jianChu) {
        java.util.List<String> yi = getYi(jianChu);
        java.util.List<String> ji = getJi(jianChu);
        String yiStr = String.join("", yi);
        String jiStr = String.join("", ji);
        return new ShareAdvice(
                yiStr.contains("\u65b0\u5efa\u5206\u4eab"),
                yiStr.contains("\u4e0a\u4f20\u6587\u4ef6"),
                yiStr.contains("\u4e0b\u8f7d\u6587\u4ef6") || yiStr.contains("\u6536\u53d6\u4ed6\u4eba\u5206\u4eab"),
                jiStr.contains("\u53d6\u6d88\u5206\u4eab")
        );
    }

    public static class ShareAdvice {
        public final boolean favorableForShare;
        public final boolean favorableForUpload;
        public final boolean favorableForDownload;
        public final boolean favorableForCancel;

        public ShareAdvice(boolean share, boolean upload, boolean download, boolean cancel) {
            this.favorableForShare = share;
            this.favorableForUpload = upload;
            this.favorableForDownload = download;
            this.favorableForCancel = cancel;
        }
    }
}
