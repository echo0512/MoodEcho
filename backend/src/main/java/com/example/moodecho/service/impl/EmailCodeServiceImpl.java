package com.example.moodecho.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.moodecho.entity.EmailVerificationCode;
import com.example.moodecho.entity.User;
import com.example.moodecho.mapper.EmailVerificationCodeMapper;
import com.example.moodecho.mapper.UserMapper;
import com.example.moodecho.service.EmailCodeService;
import com.example.moodecho.service.MailService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Random;

/**
 * 邮箱验证码服务实现类
 * 主要作用：处理用户注册时的邮箱验证码发送与验证逻辑，包括防重复注册、频率限制、验证码生成、存储及邮件发送。
 */
@Service
public class EmailCodeServiceImpl implements EmailCodeService {

    private static final Logger log = LoggerFactory.getLogger(EmailCodeServiceImpl.class);

    private static final String PURPOSE_REGISTER = "REGISTER";
    private static final String PURPOSE_RESET_PASSWORD = "RESET_PASSWORD";

    /**
     * 验证码有效期：5 分钟
     */
    private static final int EXPIRE_MINUTES = 5;

    /**
     * 同一邮箱发送间隔：60 秒
     */
    private static final int RESEND_INTERVAL_SECONDS = 60;

    private final EmailVerificationCodeMapper emailCodeMapper;
    private final UserMapper userMapper;
    private final MailService mailService;

    public EmailCodeServiceImpl(
            EmailVerificationCodeMapper emailCodeMapper,
            UserMapper userMapper,
            MailService mailService
    ) {
        this.emailCodeMapper = emailCodeMapper;
        this.userMapper = userMapper;
        this.mailService = mailService;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendRegisterCode(String email) {
        // 1. 如果邮箱已经注册，不允许继续发送注册验证码
        LambdaQueryWrapper<User> userQuery = new LambdaQueryWrapper<>();
        userQuery.eq(User::getEmail, email);

        User existUser = userMapper.selectOne(userQuery);
        if (existUser != null) {
            throw new RuntimeException("该邮箱已被注册");
        }

        // 2. 检查 60 秒内是否已经发送过
        EmailVerificationCode latestCode = getLatestCode(email, PURPOSE_REGISTER);

        if (latestCode != null && latestCode.getCreatedAt() != null) {
            LocalDateTime nextSendTime = latestCode.getCreatedAt().plusSeconds(RESEND_INTERVAL_SECONDS);
            if (LocalDateTime.now().isBefore(nextSendTime)) {
                throw new RuntimeException("验证码发送过于频繁，请稍后再试");
            }
        }

        // 3. 生成 6 位数字验证码
        String code = generateSixDigitCode();

        // 4. 保存验证码到数据库
        EmailVerificationCode emailCode = new EmailVerificationCode();
        emailCode.setEmail(email);
        emailCode.setCode(code);
        emailCode.setPurpose(PURPOSE_REGISTER);
        emailCode.setUsed(0);
        emailCode.setExpireTime(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES));

        emailCodeMapper.insert(emailCode);

        // 5. 发送邮件
        String subject = "MoodEcho 注册验证码";
        String content = buildRegisterCodeMailContent(code);

        try {
            mailService.sendTextMail(email, subject, content);
        } catch (Exception e) {
            log.error("Failed to send register verification code email to {}", email, e);
            throw new RuntimeException("验证码邮件发送失败，请检查邮箱地址或稍后再试", e);
        }
    }

    @Override
    public void verifyRegisterCode(String email, String code) {
        verifyCode(email, code, PURPOSE_REGISTER);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void sendResetPasswordCode(String email) {
        LambdaQueryWrapper<User> userQuery = new LambdaQueryWrapper<>();
        userQuery.eq(User::getEmail, email);

        User user = userMapper.selectOne(userQuery);
        if (user == null) {
            throw new RuntimeException("该邮箱尚未注册");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("账号状态异常，无法重置密码");
        }

        EmailVerificationCode latestCode = getLatestCode(email, PURPOSE_RESET_PASSWORD);
        if (latestCode != null && latestCode.getCreatedAt() != null) {
            LocalDateTime nextSendTime = latestCode.getCreatedAt().plusSeconds(RESEND_INTERVAL_SECONDS);
            if (LocalDateTime.now().isBefore(nextSendTime)) {
                throw new RuntimeException("验证码发送过于频繁，请稍后再试");
            }
        }

        String code = generateSixDigitCode();

        EmailVerificationCode emailCode = new EmailVerificationCode();
        emailCode.setEmail(email);
        emailCode.setCode(code);
        emailCode.setPurpose(PURPOSE_RESET_PASSWORD);
        emailCode.setUsed(0);
        emailCode.setExpireTime(LocalDateTime.now().plusMinutes(EXPIRE_MINUTES));

        emailCodeMapper.insert(emailCode);

        try {
            mailService.sendTextMail(email, "MoodEcho 重置密码验证码", buildResetPasswordCodeMailContent(code));
        } catch (Exception e) {
            log.error("Failed to send reset password verification code email to {}", email, e);
            throw new RuntimeException("验证码邮件发送失败，请检查邮箱地址或稍后再试", e);
        }
    }

    @Override
    public void verifyResetPasswordCode(String email, String code) {
        verifyCode(email, code, PURPOSE_RESET_PASSWORD);
    }

    private void verifyCode(String email, String code, String purpose) {
        // 查询该邮箱最新的一条未使用验证码
        LambdaQueryWrapper<EmailVerificationCode> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EmailVerificationCode::getEmail, email)
                .eq(EmailVerificationCode::getPurpose, purpose)
                .eq(EmailVerificationCode::getUsed, 0)
                .orderByDesc(EmailVerificationCode::getCreatedAt)
                .last("LIMIT 1");

        EmailVerificationCode emailCode = emailCodeMapper.selectOne(queryWrapper);

        if (emailCode == null) {
            throw new RuntimeException("请先获取验证码");
        }

        // 2. 判断是否过期
        if (LocalDateTime.now().isAfter(emailCode.getExpireTime())) {
            throw new RuntimeException("验证码已过期，请重新获取");
        }

        // 3. 判断验证码是否正确
        if (!emailCode.getCode().equals(code)) {
            throw new RuntimeException("验证码错误");
        }

        // 4. 验证成功后立即标记为已使用
        emailCode.setUsed(1);
        emailCode.setUsedAt(LocalDateTime.now());
        emailCodeMapper.updateById(emailCode);
    }

    private EmailVerificationCode getLatestCode(String email, String purpose) {
        LambdaQueryWrapper<EmailVerificationCode> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(EmailVerificationCode::getEmail, email)
                .eq(EmailVerificationCode::getPurpose, purpose)
                .orderByDesc(EmailVerificationCode::getCreatedAt)
                .last("LIMIT 1");

        return emailCodeMapper.selectOne(queryWrapper);
    }

    private String generateSixDigitCode() {
        Random random = new Random();
        int number = random.nextInt(1_000_000);
        return String.format("%06d", number);
    }

    private String buildRegisterCodeMailContent(String code) {
        return "你的 MoodEcho 注册验证码是：" + code + "\n\n"
                + "验证码 5 分钟内有效。\n"
                + "如果不是你本人操作，请忽略此邮件。";
    }

    private String buildResetPasswordCodeMailContent(String code) {
        return "你的 MoodEcho 重置密码验证码是：" + code + "\n\n"
                + "验证码 5 分钟内有效。\n"
                + "如果不是你本人操作，请忽略此邮件。";
    }
}
