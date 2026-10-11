package com.example.moodecho.vo;

import lombok.Data;

/**
 * 登录视图对象 (Login View Object)
 * 用于前端展示用户登录信息
 */
@Data
public class LoginVO {

    private String token;

    private UserVO user;
}
