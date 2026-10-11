package com.example.moodecho.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日记图片实体类
 * 对应 diary_image 表，保存日记图片的归属、文件元信息、本地存储路径和逻辑删除状态。
 */
@Data
@TableName("diary_image")
public class DiaryImage {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属日记ID
     */
    private Long diaryId;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 原始文件名
     */
    private String originalName;

    /**
     * 服务器保存后的文件名
     */
    private String storedName;

    /**
     * 文件类型
     */
    private String contentType;

    /**
     * 文件大小，单位字节
     */
    private Long fileSize;

    /**
     * 本地存储路径
     */
    private String storagePath;

    /**
     * 是否删除：0未删除，1已删除
     */
    private Integer deleted;

    /**
     * 上传时间
     */
    private LocalDateTime createdAt;

    /**
     * 修改时间
     */
    private LocalDateTime updatedAt;
}
