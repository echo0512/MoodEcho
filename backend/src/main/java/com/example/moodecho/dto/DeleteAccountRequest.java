package com.example.moodecho.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class DeleteAccountRequest {

    @NotBlank(message = "当前密码不能为空")
    private String password;
}
