package com.example.moodecho.util;

import com.example.moodecho.entity.User;
import com.example.moodecho.mapper.UserMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 身份验证工具类
 * 该类主要用于处理基于 JWT的用户身份验证逻辑。
 * 它提供了从 HTTP 请求中提取 Token、验证 Token 有效性以及获取当前登录用户 ID 的功能。
 *     从请求头 {@code Authorization} 中解析 Bearer Token
 *     校验 Token 的合法性及有效期
 *     从有效 Token 中提取用户唯一标识 (User ID)
 * 若检测到未登录、Token 缺失或 Token 无效，将抛出 {@link RuntimeException}。
 *
 */

@Component
public class AuthUtil {

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;

    public AuthUtil(JwtUtil jwtUtil, UserMapper userMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
    }

    /**
     * 从请求头 Authorization 中解析当前用户 ID
     */
    public Long getCurrentUserId(HttpServletRequest request) {
        String token = getTokenFromRequest(request);

        if (!StringUtils.hasText(token)) {
            throw new RuntimeException("未登录或登录状态已失效");
        }

        if (!jwtUtil.isTokenValid(token)) {
            throw new RuntimeException("登录状态无效或已过期");
        }

        Long userId = jwtUtil.getUserIdFromToken(token);
        User user = userMapper.selectById(userId);

        if (user == null || !Integer.valueOf(1).equals(user.getStatus())) {
            throw new RuntimeException("账号不存在或状态异常");
        }

        return userId;
    }

    /**
     * 从请求头中提取 token
     * Authorization: Bearer xxxxx
     */
    private String getTokenFromRequest(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");

        if (!StringUtils.hasText(authorization)) {
            return null;
        }

        if (!authorization.startsWith("Bearer ")) {
            return null;
        }

        return authorization.substring(7);
    }
}
