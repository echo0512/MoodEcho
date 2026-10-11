package com.example.moodecho.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {
    /**
     * 用户注册请求 DTO
     * 包含邮箱、验证码、密码及确认密码字段，并添加了相应的校验注解
     */

    /**
     * 用户邮箱
     * - 不能为空
     * - 必须符合邮箱格式
     */
    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    /**
     * 邮箱验证码
     * - 不能为空
     */
    @NotBlank(message = "验证码不能为空")
    private String code;

    /**
     * 用户密码
     * - 不能为空
     * - 长度必须在 8 到 64 位之间
     * - 必须同时包含字母和数字，可以使用下划线
     */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度应为 8 到 64 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d_]+$", message = "密码须同时包含字母和数字，且只能使用字母、数字和下划线")
    private String password;

    /**
     * 确认密码
     * - 不能为空
     * - 通常需要在 Service 层逻辑中与 password 进行比对以确保一致
     */
    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
