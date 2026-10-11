package com.example.moodecho.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 共鸣话题实体类
 * 对应 resonance_topic 表，保存第一版固定话题及启用状态。
 */
@Data
@TableName("resonance_topic")
public class ResonanceTopic {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 话题名称
     */
    private String name;

    /**
     * 话题描述
     */
    private String description;

    /**
     * 排序值，越小越靠前
     */
    private Integer sortOrder;

    /**
     * 是否启用：1启用，0停用
     */
    private Integer enabled;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间
     */
    private LocalDateTime updatedAt;
}
