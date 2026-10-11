package com.example.moodecho.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDate;

/**
 * 编辑日记请求参数
 * 接收前端修改日记时提交的新标题、新正文和新记录日期，实际可修改范围由 Service 层校验当前用户权限。
 */
@Data
public class UpdateDiaryRequest {

    @Size(max = 50, message = "标题不能超过50字")
    private String title;

    @Size(max = 10000, message = "正文不能超过10000字")
    private String content;

    @NotNull(message = "记录日期不能为空")
    private LocalDate recordDate;
}
