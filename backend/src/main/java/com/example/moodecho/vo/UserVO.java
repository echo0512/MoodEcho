package com.example.moodecho.vo;

import lombok.Data;

/**
 * 用户视图对象 (User View Object)
 * 用于前端展示用户基本信息，隐藏敏感数据（如密码等）
 */
@Data
public class UserVO {

    private Long id;

    private String email;

    private String nickname;

    private String avatarUrl;
}
