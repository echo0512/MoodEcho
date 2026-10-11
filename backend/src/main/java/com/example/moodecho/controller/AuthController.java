package com.example.moodecho.controller;

import com.example.moodecho.common.ApiResponse;
import com.example.moodecho.dto.LoginRequest;
import com.example.moodecho.dto.RegisterRequest;
import com.example.moodecho.dto.ResetPasswordRequest;
import com.example.moodecho.dto.SendRegisterCodeRequest;
import com.example.moodecho.service.AuthService;
import com.example.moodecho.service.EmailCodeService;
import com.example.moodecho.vo.LoginVO;
import com.example.moodecho.vo.UserVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final EmailCodeService emailCodeService;

    public AuthController(AuthService authService, EmailCodeService emailCodeService) {
        this.authService = authService;
        this.emailCodeService = emailCodeService;
    }

    @PostMapping("/send-register-code")
    public ApiResponse<Void> sendRegisterCode(@Valid @RequestBody SendRegisterCodeRequest request) {
        emailCodeService.sendRegisterCode(request.getEmail());
        return ApiResponse.success(null);
    }

    @PostMapping("/send-reset-password-code")
    public ApiResponse<Void> sendResetPasswordCode(@Valid @RequestBody SendRegisterCodeRequest request) {
        emailCodeService.sendResetPasswordCode(request.getEmail());
        return ApiResponse.success(null);
    }

    @PostMapping("/register")
    public ApiResponse<UserVO> register(@Valid @RequestBody RegisterRequest request) {
        UserVO userVO = authService.register(request);
        return ApiResponse.success(userVO);
    }

    @PostMapping("/login")
    public ApiResponse<LoginVO> login(@Valid @RequestBody LoginRequest request) {
        LoginVO loginVO = authService.login(request);
        return ApiResponse.success(loginVO);
    }

    @PostMapping("/reset-password")
    public ApiResponse<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest request) {
        authService.resetPassword(request);
        return ApiResponse.success(null);
    }
}
