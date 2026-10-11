package com.example.moodecho.service;

import com.example.moodecho.dto.LoginRequest;
import com.example.moodecho.dto.RegisterRequest;
import com.example.moodecho.dto.ResetPasswordRequest;
import com.example.moodecho.vo.LoginVO;
import com.example.moodecho.vo.UserVO;

public interface AuthService {

    UserVO register(RegisterRequest request);

    LoginVO login(LoginRequest request);

    void resetPassword(ResetPasswordRequest request);
}
