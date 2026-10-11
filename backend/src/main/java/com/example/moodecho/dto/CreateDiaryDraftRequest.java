package com.example.moodecho.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;

/**
 * 创建日记草稿请求 DTO
 * 用于新建页首次插入图片时创建临时草稿，允许正文为空但必须指定记录日期。
 */
@Data
public class CreateDiaryDraftRequest {

    private String title;

    private String content;

    @NotNull(message = "记录日期不能为空")
    private LocalDate recordDate;
}
