package com.example.moodecho.service;

public interface EmailCodeService {

    /**
     * 发送注册验证码
     */
    void sendRegisterCode(String email);

    /**
     * 校验注册验证码
     */
    void verifyRegisterCode(String email, String code);

    /**
     * 发送重置密码验证码
     */
    void sendResetPasswordCode(String email);

    /**
     * 校验重置密码验证码
     */
    void verifyResetPasswordCode(String email, String code);
}
