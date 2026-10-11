package com.example.moodecho.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("email_verification_code")
public class EmailVerificationCode {

    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 接收验证码的邮箱
     */
    private String email;

    /**
     * 验证码
     */
    private String code;

    /**
     * 用途：REGISTER 注册
     */
    private String purpose;

    /**
     * 是否已使用：0 未使用，1 已使用
     */
    private Integer used;

    /**
     * 过期时间
     */
    private LocalDateTime expireTime;

    /**
     * 创建时间
     */
    private LocalDateTime createdAt;

    /**
     * 使用时间
     */
    private LocalDateTime usedAt;
}