package com.example.moodecho.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("app_user") //这个 Java 类对应数据库里的 app_user 表。
public class User {

    @TableId(type = IdType.AUTO) //id 是主键，并且由数据库自增生成。
    private Long id;

    /**
     * 邮箱，作为登录账号
     */
    private String email;

    /**
     * 加密后的密码
     */
    private String passwordHash;

    /**
     * 昵称，注册后自动生成，用户后续可以修改
     */
    private String nickname;

    /**
     * 头像地址
     */
    private String avatarUrl;

    /**
     * 账号状态：1 正常，0 禁用
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
