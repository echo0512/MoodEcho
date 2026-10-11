package com.example.moodecho.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 创建匿名共鸣投递请求参数。
 */
@Data
public class CreateResonancePostRequest {

    private Long diaryId;

    private Long topicId;

    @NotBlank(message = "投递内容不能为空")
    private String content;
}
