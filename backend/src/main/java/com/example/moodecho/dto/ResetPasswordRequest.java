package com.example.moodecho.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ResetPasswordRequest {

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;

    @NotBlank(message = "验证码不能为空")
    private String code;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 64, message = "密码长度应为 8 到 64 位")
    @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d)[A-Za-z\\d_]+$", message = "密码须同时包含字母和数字，且只能使用字母、数字和下划线")
    private String password;

    @NotBlank(message = "确认密码不能为空")
    private String confirmPassword;
}
