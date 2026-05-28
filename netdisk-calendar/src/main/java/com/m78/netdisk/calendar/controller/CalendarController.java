package com.m78.netdisk.calendar.controller;

import com.m78.netdisk.calendar.domain.vo.CalendarVO;
import com.m78.netdisk.calendar.util.LunarCalendarUtil;
import com.m78.netdisk.common.domain.R;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    @GetMapping("/today")
    public R<CalendarVO> today() {
        LocalDate now = LocalDate.now();
        LunarCalendarUtil.LunarDate lunar = LunarCalendarUtil.solarToLunar(
                now.getYear(), now.getMonthValue(), now.getDayOfMonth());

        CalendarVO.LunarInfo lunarInfo = CalendarVO.LunarInfo.builder()
                .year(lunar.heavenlyStem + lunar.earthlyBranch)
                .month(lunar.monthName)
                .day(lunar.dayName)
                .zodiac(lunar.zodiac)
                .heavenlyStem(lunar.heavenlyStem)
                .earthlyBranch(lunar.earthlyBranch)
                .monthStem(lunar.monthStem)
                .monthBranch(lunar.monthBranch)
                .dayStem(lunar.dayStem)
                .dayBranch(lunar.dayBranch)
                .jieQi(lunar.jieQi)
                .isLeapMonth(lunar.isLeap)
                .jianChu(lunar.jianChu)
                .build();

        LunarCalendarUtil.ShareAdvice advice = LunarCalendarUtil.getShareAdvice(lunar.jianChu);

        CalendarVO.ShareAdvice shareAdvice = CalendarVO.ShareAdvice.builder()
                .favorableForShare(advice.favorableForShare)
                .favorableForUpload(advice.favorableForUpload)
                .favorableForDownload(advice.favorableForDownload)
                .favorableForCancel(advice.favorableForCancel)
                .build();

        CalendarVO vo = CalendarVO.builder()
                .date(now.toString())
                .lunar(lunarInfo)
                .yi(LunarCalendarUtil.getYi(lunar.jianChu))
                .ji(LunarCalendarUtil.getJi(lunar.jianChu))
                .shareAdvice(shareAdvice)
                .build();

        return R.ok(vo);
    }
}
