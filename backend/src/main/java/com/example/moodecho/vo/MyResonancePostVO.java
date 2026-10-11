package com.example.moodecho.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 我的匿名投递响应视图对象。
 */
@Data
public class MyResonancePostVO {

    private Long id;

    private String content;

    private Long topicId;

    private String topicName;

    private LocalDateTime createdAt;
}
