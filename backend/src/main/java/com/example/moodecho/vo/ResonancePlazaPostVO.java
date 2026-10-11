package com.example.moodecho.vo;

import lombok.Data;

/**
 * 共鸣广场匿名内容响应视图对象，不包含用户身份和来源日记信息。
 */
@Data
public class ResonancePlazaPostVO {

    private Long id;

    private String content;

    private Long topicId;

    private String topicName;
}
