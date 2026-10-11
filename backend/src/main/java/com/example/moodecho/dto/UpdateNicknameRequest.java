package com.example.moodecho.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 修改昵称请求 DTO，接收前端提交的新昵称并提供基础参数校验。
 */
@Data
public class UpdateNicknameRequest {

    @NotBlank(message = "昵称不能为空")
    @Size(min = 2, max = 10, message = "昵称长度应为 2 到 10 个字符")
    private String nickname;
}
