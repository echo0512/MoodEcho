package com.example.moodecho.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.moodecho.entity.EmailVerificationCode;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface EmailVerificationCodeMapper extends BaseMapper<EmailVerificationCode> {
}