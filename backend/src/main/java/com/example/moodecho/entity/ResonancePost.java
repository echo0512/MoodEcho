package com.example.moodecho.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 匿名共鸣投递实体类
 * 对应 resonance_post 表，保存用户主动投递后的纯文本匿名内容快照。
 */
@Data
@TableName("resonance_post")
public class ResonancePost {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 投递用户ID，仅用于本人管理和权限校验，广场接口不返回。
     */
    private Long userId;

    /**
     * 来源日记ID，可为空。
     */
    private Long diaryId;

    /**
     * 所属话题ID，可为空。
     */
    private Long topicId;

    /**
     * 匿名投递纯文本内容。
     */
    private String content;

    /**
     * 状态：1已发布，2已撤回。
     */
    private Integer status;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间
     */
    private LocalDateTime updatedAt;

    /**
     * 撤回时间
     */
    private LocalDateTime withdrawnAt;
}
