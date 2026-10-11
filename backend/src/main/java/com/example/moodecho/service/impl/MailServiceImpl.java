package com.example.moodecho.service.impl;

import com.example.moodecho.service.MailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 邮件服务实现类
 * 负责处理简单的文本邮件发送业务
 */
@Service
public class MailServiceImpl implements MailService {

    /**
     * Spring提供的邮件发送器接口
     */
    private final JavaMailSender javaMailSender;

    /**
     * 发件人邮箱地址
     * 从配置文件中读取 spring.mail.username 属性
     */
    @Value("${spring.mail.username}")
    private String from;

    /**
     * 构造函数注入 JavaMailSender
     *
     * @param javaMailSender 邮件发送器实例
     */
    public MailServiceImpl(JavaMailSender javaMailSender) {
        this.javaMailSender = javaMailSender;
    }

    /**
     * 发送纯文本邮件
     *
     * @param to      收件人邮箱地址
     * @param subject 邮件主题
     * @param content 邮件正文内容
     * @throws RuntimeException 当收件人邮箱为空时抛出异常
     */
    @Override
    public void sendTextMail(String to, String subject, String content) {
        // 校验收件人邮箱是否为空
        if (!StringUtils.hasText(to)) {
            throw new RuntimeException("收件人邮箱不能为空");
        }

        // 创建简单邮件消息对象
        SimpleMailMessage message = new SimpleMailMessage();
        // 设置发件人
        message.setFrom(from);
        // 设置收件人
        message.setTo(to);
        // 设置邮件主题
        message.setSubject(subject);
        // 设置邮件正文
        message.setText(content);

        // 执行发送操作
        javaMailSender.send(message);
    }
}