package com.example.moodecho.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 新建日记请求参数
 * 接收前端创建日记时提交的标题、正文和记录日期，不接收 userId，归属用户由登录 token 决定。
 */
@Data
public class CreateDiaryRequest {

    @Size(max = 50, message = "标题不能超过50字")
    private String title;

    @Size(max = 10000, message = "正文不能超过10000字")
    private String content;

    @NotNull(message = "记录日期不能为空")
    private LocalDate recordDate;
}
