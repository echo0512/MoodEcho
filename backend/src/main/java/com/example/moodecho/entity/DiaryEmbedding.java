package com.example.moodecho.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日记语义向量实体。
 * 仅保存当前用户正式日记的文本向量，不向前端返回原始向量内容。
 */
@Data
@TableName("diary_embedding")
public class DiaryEmbedding {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long diaryId;

    private Long userId;

    /** Versioned JSON snapshot: whole diary vector plus ordered text/vector chunks. */
    private String embedding;

    private String modelName;

    private String contentHash;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
