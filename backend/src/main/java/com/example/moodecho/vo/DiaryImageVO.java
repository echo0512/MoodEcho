package com.example.moodecho.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 日记图片响应视图对象
 * 向前端返回图片元信息、正文可插入的 Markdown 标记和图片查看地址。
 */
@Data
public class DiaryImageVO {

    private Long id;

    private Long diaryId;

    private String originalName;

    private String contentType;

    private Long fileSize;

    /**
     * 正文里插入的图片标记
     * 例如：![图片](moodecho-image:12)
     */
    private String markdownTag;

    /**
     * 前端查看图片时用的接口地址
     */
    private String viewUrl;

    private LocalDateTime createdAt;
}
