package com.example.moodecho.controller;

import com.example.moodecho.common.ApiResponse;
import com.example.moodecho.dto.ChangePasswordRequest;
import com.example.moodecho.dto.DeleteAccountRequest;
import com.example.moodecho.dto.UpdateNicknameRequest;
import com.example.moodecho.service.UserService;
import com.example.moodecho.util.AuthUtil;
import com.example.moodecho.vo.UserStatsVO;
import com.example.moodecho.vo.UserVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户接口控制器，提供当前登录用户信息查询和个人资料修改能力。
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;
    private final AuthUtil authUtil;

    public UserController(UserService userService, AuthUtil authUtil) {
        this.userService = userService;
        this.authUtil = authUtil;
    }

    @GetMapping("/me")
    public ApiResponse<UserVO> getCurrentUser(HttpServletRequest request) {
        // 从请求中获取当前登录用户的ID
        Long currentUserId = authUtil.getCurrentUserId(request);
        // 根据用户ID查询并构建用户视图对象
        UserVO userVO = userService.getCurrentUser(currentUserId);
        return ApiResponse.success(userVO);
    }

    @GetMapping("/me/stats")
    public ApiResponse<UserStatsVO> getCurrentUserStats(HttpServletRequest request) {
        Long currentUserId = authUtil.getCurrentUserId(request);
        UserStatsVO statsVO = userService.getCurrentUserStats(currentUserId);
        return ApiResponse.success(statsVO);
    }

    @PutMapping("/me/nickname")
    public ApiResponse<UserVO> updateNickname(
            HttpServletRequest request,
            @Valid @RequestBody UpdateNicknameRequest updateNicknameRequest
    ) {
        Long currentUserId = authUtil.getCurrentUserId(request);
        UserVO userVO = userService.updateNickname(currentUserId, updateNicknameRequest.getNickname());
        return ApiResponse.success(userVO);
    }

    @PutMapping("/me/password")
    public ApiResponse<Void> changePassword(
            HttpServletRequest request,
            @Valid @RequestBody ChangePasswordRequest changePasswordRequest
    ) {
        Long currentUserId = authUtil.getCurrentUserId(request);
        userService.changePassword(currentUserId, changePasswordRequest);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(
            HttpServletRequest request,
            @Valid @RequestBody DeleteAccountRequest deleteAccountRequest
    ) {
        Long currentUserId = authUtil.getCurrentUserId(request);
        userService.deleteAccount(currentUserId, deleteAccountRequest);
        return ApiResponse.success(null);
    }
}
