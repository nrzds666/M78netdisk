package com.m78.netdisk.calendar.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CalendarVO {

    private String date;
    private LunarInfo lunar;
    private List<String> yi;
    private List<String> ji;
    private ShareAdvice shareAdvice;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LunarInfo {
        private String year;          // 农历年（丙午）
        private String month;         // 农历月（四月）
        private String day;           // 农历日（十二）
        private String zodiac;        // 生肖（马）
        private String heavenlyStem;  // 年天干（丙）
        private String earthlyBranch; // 年地支（午）
        private String monthStem;     // 月天干
        private String monthBranch;   // 月地支
        private String dayStem;       // 日天干
        private String dayBranch;     // 日地支
        private String jieQi;         // 节气段
        private boolean isLeapMonth;  // 是否闰月
        private String jianChu;       // 建除
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ShareAdvice {
        private boolean favorableForShare;
        private boolean favorableForUpload;
        private boolean favorableForDownload;
        private boolean favorableForCancel;
    }
}
