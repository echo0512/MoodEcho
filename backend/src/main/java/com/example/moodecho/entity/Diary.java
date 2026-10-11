package com.example.moodecho.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 私密日记实体类
 * 对应数据库 diary 表，保存用户日记正文、记录日期、草稿状态、逻辑删除状态以及创建/更新时间。
 */
@Data
@TableName("diary")
public class Diary {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 所属用户ID
     */
    private Long userId;

    /**
     * 日记标题，可选
     */
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String title;

    /**
     * 日记正文
     */
    private String content;

    /**
     * 记录日期
     */
    private LocalDate recordDate;

    /**
     * 是否删除：0未删除，1已删除
     */
    private Integer deleted;

    /**
     * 日记状态：0 草稿，1 正式
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
}
