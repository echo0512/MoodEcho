package com.example.moodecho.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.moodecho.dto.LoginRequest;
import com.example.moodecho.dto.RegisterRequest;
import com.example.moodecho.dto.ResetPasswordRequest;
import com.example.moodecho.entity.User;
import com.example.moodecho.mapper.UserMapper;
import com.example.moodecho.service.AuthService;
import com.example.moodecho.service.EmailCodeService;
import com.example.moodecho.util.JwtUtil;
import com.example.moodecho.vo.LoginVO;
import com.example.moodecho.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthServiceImpl implements AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final EmailCodeService emailCodeService;

    public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil, EmailCodeService emailCodeService) {
        this.userMapper = userMapper;
        this.passwordEncoder = passwordEncoder;
        this.jwtUtil = jwtUtil;
        this.emailCodeService = emailCodeService;
    }

    /**
     * 用户注册
     * 使用事务保证用户插入和昵称更新的原子性
     */
    @Override
    @Transactional
    public UserVO register(RegisterRequest request) {
        // 1. 校验两次密码是否一致
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        // 2. 校验真正的邮箱验证码
        emailCodeService.verifyRegisterCode(request.getEmail(), request.getCode());

        // 3. 判断邮箱是否已经注册
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, request.getEmail());

        User existUser = userMapper.selectOne(queryWrapper);
        if (existUser != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        // 4. 创建用户对象
        User user = new User();
        user.setEmail(request.getEmail());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setStatus(1);

        // 5. 先插入数据库，拿到自增 ID
        userMapper.insert(user);

        // 6. 根据用户 ID 生成默认昵称
        user.setNickname("MoodEcho用户" + user.getId());

        // 7. 更新昵称
        userMapper.updateById(user);

        // 8. 返回用户基本信息
        return toUserVO(user);
    }

    @Override
    public LoginVO login(LoginRequest request) {
        // 1. 根据邮箱查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, request.getEmail());

        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new RuntimeException("邮箱或密码错误");
        }

        // 2. 判断账号状态
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("账号状态异常，无法登录");
        }

        // 3. 校验密码
        boolean matched = passwordEncoder.matches(
                request.getPassword(),
                user.getPasswordHash()
        );

        if (!matched) {
            throw new RuntimeException("邮箱或密码错误");
        }

        // 4. 使用JWT生成 token
        String token = jwtUtil.generateToken(user.getId(), user.getEmail());

        // 5. 组装返回结果
        LoginVO loginVO = new LoginVO();
        loginVO.setToken(token);
        loginVO.setUser(toUserVO(user));

        return loginVO;
    }

    @Override
    @Transactional
    public void resetPassword(ResetPasswordRequest request) {
        if (!request.getPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getEmail, request.getEmail());

        User user = userMapper.selectOne(queryWrapper);
        if (user == null) {
            throw new RuntimeException("该邮箱尚未注册");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("账号状态异常，无法重置密码");
        }

        emailCodeService.verifyResetPasswordCode(request.getEmail(), request.getCode());

        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        userMapper.updateById(user);
    }

    private UserVO toUserVO(User user) {
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setEmail(user.getEmail());
        userVO.setNickname(user.getNickname());
        userVO.setAvatarUrl(user.getAvatarUrl());
        return userVO;
    }
}
