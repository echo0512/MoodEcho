package com.example.moodecho.service;

import com.example.moodecho.dto.ChangePasswordRequest;
import com.example.moodecho.dto.DeleteAccountRequest;
import com.example.moodecho.vo.UserStatsVO;
import com.example.moodecho.vo.UserVO;

public interface UserService {

    UserVO getCurrentUser(Long userId);

    UserVO updateNickname(Long userId, String nickname);

    UserStatsVO getCurrentUserStats(Long userId);

    void changePassword(Long userId, ChangePasswordRequest request);

    void deleteAccount(Long userId, DeleteAccountRequest request);
}
