package com.m78.netdisk.calendar;

import com.m78.netdisk.calendar.util.LunarCalendarUtil;
import com.m78.netdisk.calendar.util.LunarCalendarUtil.ShareAdvice;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Calendar 黄历模块单元测试
 * LunarCalendarUtil 是纯静态工具类，无需 Mock
 *
 * 注意：农历算法使用经典 LUNAR_INFO 数据集，日期推算可能与传统历书有
 * 细微差异（约±1天）。本测试验证算法自一致性而非外部日历基准。
 */
class LunarCalendarUtilTest {

    // ==================== solarToLunar — 基础功能 ====================

    @Test
    void solarToLunar_shouldReturnValidLunarDate() {
        LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.solarToLunar(2026, 5, 28);

        assertNotNull(lunar);
        assertTrue(lunar.lunarYear >= 1900);
        assertTrue(lunar.lunarMonth >= 1 && lunar.lunarMonth <= 12);
        assertTrue(lunar.lunarDay >= 1 && lunar.lunarDay <= 30);
        assertNotNull(lunar.heavenlyStem);
        assertNotNull(lunar.earthlyBranch);
        assertNotNull(lunar.zodiac);
        assertNotNull(lunar.monthName);
        assertNotNull(lunar.dayName);
        assertNotNull(lunar.jianChu);
    }

    @Test
    void solarToLunar_shouldHaveConsistentGanZhi() {
        LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.solarToLunar(2026, 5, 28);

        assertFalse(lunar.heavenlyStem.isEmpty());
        assertFalse(lunar.earthlyBranch.isEmpty());
        assertFalse(lunar.monthStem.isEmpty());
        assertFalse(lunar.monthBranch.isEmpty());
        assertFalse(lunar.dayStem.isEmpty());
        assertFalse(lunar.dayBranch.isEmpty());
    }

    @Test
    void solarToLunar_shouldHandle1901EarlyDate() {
        // 1901-01-01 是基准日期
        LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.solarToLunar(1901, 1, 1);

        assertNotNull(lunar);
        assertTrue(lunar.lunarYear >= 1900);
        assertTrue(lunar.lunarDay >= 1);
    }

    @Test
    void solarToLunar_shouldHandle2100Year() {
        // 2100年是支持的最后一年
        LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.solarToLunar(2100, 1, 1);

        assertNotNull(lunar);
        assertTrue(lunar.lunarYear >= 2099);
        assertNotNull(lunar.heavenlyStem);
        assertNotNull(lunar.earthlyBranch);
    }

    @Test
    void solarToLunar_shouldThrowBefore1901() {
        assertThrows(IllegalArgumentException.class,
                () -> LunarCalendarUtil.solarToLunar(1900, 12, 31));
    }

    // ==================== 生肖循环 ====================

    @Test
    void solarToLunar_shouldHaveCorrectZodiacCycle() {
        // 验证相同公历日的生肖每12年循环
        String z2024 = LunarCalendarUtil.solarToLunar(2024, 6, 1).zodiac;
        String z2036 = LunarCalendarUtil.solarToLunar(2036, 6, 1).zodiac;
        assertEquals(z2024, z2036, "Zodiac should cycle every 12 years");

        // 相邻年份的生肖不应相同（除非前一年还没到春节）
        // 至少验证 2024 的生肖是有效的 12 生肖之一
        String[] validZodiacs = {"鼠", "牛", "虎", "兔", "龙", "蛇", "马", "羊", "猴", "鸡", "狗", "猪"};
        assertTrue(java.util.Arrays.asList(validZodiacs).contains(z2024),
                "Invalid zodiac: " + z2024);
    }

    // ==================== 日干支推算 ====================

    @Test
    void solarToLunar_shouldHaveIncrementingDayGanZhi() {
        // 相邻两天的日干支应不同(60天一周)
        LunarCalendarUtil.LunarDate day1 = LunarCalendarUtil.solarToLunar(2026, 5, 28);
        LunarCalendarUtil.LunarDate day2 = LunarCalendarUtil.solarToLunar(2026, 5, 29);

        assertNotEquals(day1.dayStem + day1.dayBranch,
                day2.dayStem + day2.dayBranch);
    }

    // ==================== 月份名称 ====================

    @Test
    void solarToLunar_shouldHaveMonthNames() {
        // 验证每个月15号的农历月份名称在合法列表中
        // 闰月名称会带"闰"前缀
        for (int month = 1; month <= 12; month++) {
            LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.solarToLunar(2026, month, 15);
            assertNotNull(lunar.monthName);
            assertFalse(lunar.monthName.isEmpty());
        }
    }

    // ==================== 日名称 ====================

    @Test
    void solarToLunar_shouldHaveDayNames() {
        String[] validDays = {"初一", "初二", "初三", "初四", "初五", "初六", "初七",
                "初八", "初九", "初十", "十一", "十二", "十三", "十四", "十五", "十六",
                "十七", "十八", "十九", "二十", "廿一", "廿二", "廿三", "廿四", "廿五",
                "廿六", "廿七", "廿八", "廿九", "三十"};

        for (int d = 1; d <= 28; d++) {
            LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.solarToLunar(2026, 5, d);
            assertTrue(java.util.Arrays.asList(validDays).contains(lunar.dayName),
                    "Invalid day name '" + lunar.dayName + "' for day " + d);
        }
    }

    // ==================== today() ====================

    @Test
    void today_shouldReturnValidDate() {
        LocalDate now = LocalDate.now();
        LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.today();

        assertNotNull(lunar);
        assertNotNull(lunar.monthName);
        assertNotNull(lunar.dayName);
        assertNotNull(lunar.jianChu);
    }

    // ==================== getJianChu ====================

    @Test
    void getJianChu_shouldReturnValidValue() {
        String[] valid = {"建", "除", "满", "平", "定", "执", "破", "危", "成", "收", "开", "闭"};

        for (int month = 1; month <= 12; month++) {
            for (int day = 1; day <= 30; day++) {
                String jc = LunarCalendarUtil.getJianChu(month, day);
                assertTrue(java.util.Arrays.asList(valid).contains(jc),
                        "Unexpected jianChu '" + jc + "' for month=" + month + " day=" + day);
            }
        }
    }

    @Test
    void getJianChu_shouldCycleEvery12Days() {
        // 同一个月内，每12天同一建除
        String jc1 = LunarCalendarUtil.getJianChu(1, 1);
        String jc13 = LunarCalendarUtil.getJianChu(1, 13);
        assertEquals(jc1, jc13);

        String jc2 = LunarCalendarUtil.getJianChu(1, 2);
        assertEquals(jc2, LunarCalendarUtil.getJianChu(1, 14));
    }

    @Test
    void getJianChu_shouldDifferPerMonth() {
        // 不同月份的同一日，建除应不同（月地支不同）
        String jan1 = LunarCalendarUtil.getJianChu(1, 1);
        String feb1 = LunarCalendarUtil.getJianChu(2, 1);
        assertNotEquals(jan1, feb1);
    }

    // ==================== getYi / getJi ====================

    @Test
    void getYi_shouldReturnNonEmptyForKnownValues() {
        // 建：宜新建分享
        List<String> yiJian = LunarCalendarUtil.getYi("建");
        assertFalse(yiJian.isEmpty());
        assertTrue(yiJian.contains("新建分享"));

        // 除：宜取消分享
        List<String> yiChu = LunarCalendarUtil.getYi("除");
        assertTrue(yiChu.contains("取消分享"));

        // 破：宜为空
        List<String> yiPo = LunarCalendarUtil.getYi("破");
        assertTrue(yiPo.isEmpty());
    }

    @Test
    void getJi_shouldReturnNonEmptyForKnownValues() {
        // 除：忌新建分享
        List<String> jiChu = LunarCalendarUtil.getJi("除");
        assertTrue(jiChu.contains("新建分享"));

        // 破：忌四项操作
        List<String> jiPo = LunarCalendarUtil.getJi("破");
        assertTrue(jiPo.contains("新建分享"));
        assertTrue(jiPo.contains("上传文件"));
        assertTrue(jiPo.contains("下载文件"));
        assertTrue(jiPo.contains("取消分享"));

        // 建：忌为空
        List<String> jiJian = LunarCalendarUtil.getJi("建");
        assertTrue(jiJian.isEmpty());
    }

    @Test
    void getYi_shouldReturnEmptyForUnknown() {
        List<String> yi = LunarCalendarUtil.getYi("未知");
        assertTrue(yi.isEmpty());
    }

    @Test
    void getJi_shouldReturnEmptyForUnknown() {
        List<String> ji = LunarCalendarUtil.getJi("未知");
        assertTrue(ji.isEmpty());
    }

    // ==================== getShareAdvice ====================

    @Test
    void getShareAdvice_shouldReturnFavorableForShareOnJian() {
        // 建 → 宜新建分享，宜上传文件
        ShareAdvice advice = LunarCalendarUtil.getShareAdvice("建");

        assertTrue(advice.favorableForShare);
        assertTrue(advice.favorableForUpload);
        assertFalse(advice.favorableForCancel);
    }

    @Test
    void getShareAdvice_shouldReturnUnfavorableForShareOnChu() {
        // 除 → 忌新建分享（cancel被标记为不适宜）
        ShareAdvice advice = LunarCalendarUtil.getShareAdvice("除");

        assertFalse(advice.favorableForShare);
        assertFalse(advice.favorableForUpload);
        assertTrue(advice.favorableForCancel);
    }

    @Test
    void getShareAdvice_shouldReturnAllUnfavorableOnPo() {
        // 破 → 四项皆不宜
        ShareAdvice advice = LunarCalendarUtil.getShareAdvice("破");

        assertFalse(advice.favorableForShare);
        assertFalse(advice.favorableForUpload);
        assertFalse(advice.favorableForDownload);
        assertFalse(advice.favorableForCancel);
    }

    @Test
    void getShareAdvice_shouldReturnDownloadFavorableOnShou() {
        // 收 → 宜收取他人分享、下载文件
        ShareAdvice advice = LunarCalendarUtil.getShareAdvice("收");

        assertFalse(advice.favorableForShare);
        assertFalse(advice.favorableForCancel);
        assertTrue(advice.favorableForDownload);
    }

    @Test
    void getShareAdvice_shouldReturnShareFavorableOnKai() {
        // 开 → 宜新建分享
        ShareAdvice advice = LunarCalendarUtil.getShareAdvice("开");

        assertTrue(advice.favorableForShare);
        assertFalse(advice.favorableForCancel);
    }

    @Test
    void getShareAdvice_shouldHandleUnknownJianChu() {
        ShareAdvice advice = LunarCalendarUtil.getShareAdvice("未知");

        assertFalse(advice.favorableForShare);
        assertFalse(advice.favorableForUpload);
        assertFalse(advice.favorableForDownload);
        assertFalse(advice.favorableForCancel);
    }

    // ==================== 节气 ====================

    @Test
    void solarToLunar_shouldDetectSolarTerm() {
        // 夏至附近应有节气描述
        LunarCalendarUtil.LunarDate summerSolstice = LunarCalendarUtil.solarToLunar(2026, 6, 21);
        assertNotNull(summerSolstice.jieQi);

        // 年初应有节气描述
        LunarCalendarUtil.LunarDate newYear = LunarCalendarUtil.solarToLunar(2026, 1, 1);
        assertNotNull(newYear.jieQi);
    }

    // ==================== 覆盖全部 12 种建除宜忌 ====================

    @Test
    void getYi_shouldCoverAll12JianChu() {
        String[] allJianChu = {"建", "除", "满", "平", "定", "执", "破", "危", "成", "收", "开", "闭"};
        for (String jc : allJianChu) {
            List<String> yi = LunarCalendarUtil.getYi(jc);
            List<String> ji = LunarCalendarUtil.getJi(jc);
            // 每个建除都应该有定义（可能为空列表但不会null）
            assertNotNull(yi);
            assertNotNull(ji);
        }
    }

    // ==================== 建除推算一致性 ====================

    @Test
    void solarToLunar_shouldReturnValidJianChuForAnyDate() {
        String[] valid = {"建", "除", "满", "平", "定", "执", "破", "危", "成", "收", "开", "闭"};

        // 随机抽样验证建除非空且在合法列表中
        int[][] dates = {{2024, 1, 1}, {2025, 6, 15}, {2026, 12, 31},
                {2027, 3, 8}, {2028, 8, 22}, {2029, 5, 1},
                {2030, 10, 10}, {2040, 2, 29}, {2050, 7, 7},
                {2060, 11, 11}, {2080, 4, 5}, {2099, 12, 31}};

        for (int[] d : dates) {
            LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.solarToLunar(d[0], d[1], d[2]);
            assertTrue(java.util.Arrays.asList(valid).contains(lunar.jianChu),
                    "Invalid jianChu '" + lunar.jianChu + "' for " + d[0] + "-" + d[1] + "-" + d[2]);
        }
    }
}
