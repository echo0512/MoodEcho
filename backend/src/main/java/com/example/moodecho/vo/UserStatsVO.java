package com.example.moodecho.vo;

import lombok.Data;

import java.time.LocalDate;

/**
 * 当前用户记录统计返回对象。
 */
@Data
public class UserStatsVO {

    /**
     * 正式日记总篇数。
     */
    private Integer totalDiaryCount;

    /**
     * 有记录的天数。同一天多篇日记只算 1 天。
     */
    private Integer totalRecordDays;

    /**
     * 正文纯文字总字数，不统计 HTML 标签和图片占位。
     */
    private Integer totalWordCount;

    /**
     * 最早记录日期。
     */
    private LocalDate firstRecordDate;

    /**
     * 最近记录日期。
     */
    private LocalDate latestRecordDate;
}
