package com.example.moodecho.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 日记响应视图对象
 * 用于向前端返回日记可展示字段，隐藏 userId 和 deleted 等后端权限/状态字段。
 */
@Data
public class DiaryVO {

    private Long id;

    private String title;

    private String content;

    private LocalDate recordDate;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    private List<DiaryImageVO> images;
}
