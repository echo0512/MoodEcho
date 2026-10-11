package com.example.moodecho.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.moodecho.dto.ChangePasswordRequest;
import com.example.moodecho.dto.DeleteAccountRequest;
import com.example.moodecho.entity.Diary;
import com.example.moodecho.entity.DiaryEmbedding;
import com.example.moodecho.entity.DiaryImage;
import com.example.moodecho.entity.EmailVerificationCode;
import com.example.moodecho.entity.ResonancePost;
import com.example.moodecho.entity.User;
import com.example.moodecho.mapper.DiaryEmbeddingMapper;
import com.example.moodecho.mapper.DiaryImageMapper;
import com.example.moodecho.mapper.DiaryMapper;
import com.example.moodecho.mapper.EmailVerificationCodeMapper;
import com.example.moodecho.mapper.ResonancePostMapper;
import com.example.moodecho.mapper.UserMapper;
import com.example.moodecho.service.UserService;
import com.example.moodecho.storage.ImageStorageService;
import com.example.moodecho.vo.UserStatsVO;
import com.example.moodecho.vo.UserVO;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 用户服务实现类
 * 该类实现了 UserService 接口，负责处理与用户相关的业务逻辑。
 * 主要功能包括获取当前用户信息、修改当前用户昵称，以及将用户实体转换为前端视图对象。
 */
@Service
public class UserServiceImpl implements UserService {

    private final UserMapper userMapper;
    private final DiaryMapper diaryMapper;
    private final DiaryImageMapper diaryImageMapper;
    private final DiaryEmbeddingMapper diaryEmbeddingMapper;
    private final ResonancePostMapper resonancePostMapper;
    private final EmailVerificationCodeMapper emailVerificationCodeMapper;
    private final ImageStorageService imageStorageService;
    private final PasswordEncoder passwordEncoder;

    public UserServiceImpl(
            UserMapper userMapper,
            DiaryMapper diaryMapper,
            DiaryImageMapper diaryImageMapper,
            DiaryEmbeddingMapper diaryEmbeddingMapper,
            ResonancePostMapper resonancePostMapper,
            EmailVerificationCodeMapper emailVerificationCodeMapper,
            ImageStorageService imageStorageService,
            PasswordEncoder passwordEncoder
    ) {
        this.userMapper = userMapper;
        this.diaryMapper = diaryMapper;
        this.diaryImageMapper = diaryImageMapper;
        this.diaryEmbeddingMapper = diaryEmbeddingMapper;
        this.resonancePostMapper = resonancePostMapper;
        this.emailVerificationCodeMapper = emailVerificationCodeMapper;
        this.imageStorageService = imageStorageService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public UserVO getCurrentUser(Long userId) {
        User user = userMapper.selectById(userId);

        checkUserAvailable(user);

        return toUserVO(user);
    }

    @Override
    public UserVO updateNickname(Long userId, String nickname) {
        User user = userMapper.selectById(userId);

        checkUserAvailable(user);

        String trimmedNickname = nickname == null ? "" : nickname.trim();

        if (!StringUtils.hasText(trimmedNickname)) {
            throw new RuntimeException("昵称不能为空");
        }

        if (trimmedNickname.length() < 2 || trimmedNickname.length() > 10) {
            throw new RuntimeException("昵称长度应为 2 到 10 个字符");
        }

        user.setNickname(trimmedNickname);
        userMapper.updateById(user);

        User updatedUser = userMapper.selectById(userId);
        return toUserVO(updatedUser);
    }

    @Override
    public UserStatsVO getCurrentUserStats(Long userId) {
        User user = userMapper.selectById(userId);
        checkUserAvailable(user);

        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getUserId, userId)
                .eq(Diary::getDeleted, 0)
                .eq(Diary::getStatus, 1)
                .select(Diary::getId, Diary::getContent, Diary::getRecordDate);

        List<Diary> diaries = diaryMapper.selectList(queryWrapper);

        UserStatsVO statsVO = new UserStatsVO();
        statsVO.setTotalDiaryCount(diaries.size());

        Set<LocalDate> recordDates = new HashSet<>();
        int totalWordCount = 0;
        LocalDate firstRecordDate = null;
        LocalDate latestRecordDate = null;

        for (Diary diary : diaries) {
            LocalDate recordDate = diary.getRecordDate();

            if (recordDate != null) {
                recordDates.add(recordDate);

                if (firstRecordDate == null || recordDate.isBefore(firstRecordDate)) {
                    firstRecordDate = recordDate;
                }

                if (latestRecordDate == null || recordDate.isAfter(latestRecordDate)) {
                    latestRecordDate = recordDate;
                }
            }

            totalWordCount += countPlainTextCharacters(diary.getContent());
        }

        statsVO.setTotalRecordDays(recordDates.size());
        statsVO.setTotalWordCount(totalWordCount);
        statsVO.setFirstRecordDate(firstRecordDate);
        statsVO.setLatestRecordDate(latestRecordDate);

        return statsVO;
    }

    @Override
    @Transactional
    public void changePassword(Long userId, ChangePasswordRequest request) {
        User user = userMapper.selectById(userId);
        checkUserAvailable(user);

        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            throw new RuntimeException("两次输入的密码不一致");
        }

        if (!passwordEncoder.matches(request.getOldPassword(), user.getPasswordHash())) {
            throw new RuntimeException("旧密码错误");
        }

        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        userMapper.updateById(user);
    }

    @Override
    @Transactional
    public void deleteAccount(Long userId, DeleteAccountRequest request) {
        User user = userMapper.selectById(userId);
        checkUserAvailable(user);

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new RuntimeException("当前密码错误");
        }

        LambdaQueryWrapper<DiaryImage> imageQueryWrapper = new LambdaQueryWrapper<>();
        imageQueryWrapper.eq(DiaryImage::getUserId, userId);
        List<String> imageStoragePaths = diaryImageMapper.selectList(imageQueryWrapper)
                .stream()
                .map(DiaryImage::getStoragePath)
                .filter(StringUtils::hasText)
                .toList();

        LambdaQueryWrapper<ResonancePost> resonancePostQueryWrapper = new LambdaQueryWrapper<>();
        resonancePostQueryWrapper.eq(ResonancePost::getUserId, userId);
        resonancePostMapper.delete(resonancePostQueryWrapper);

        LambdaQueryWrapper<DiaryEmbedding> embeddingQueryWrapper = new LambdaQueryWrapper<>();
        embeddingQueryWrapper.eq(DiaryEmbedding::getUserId, userId);
        diaryEmbeddingMapper.delete(embeddingQueryWrapper);

        diaryImageMapper.delete(imageQueryWrapper);

        LambdaQueryWrapper<Diary> diaryQueryWrapper = new LambdaQueryWrapper<>();
        diaryQueryWrapper.eq(Diary::getUserId, userId);
        diaryMapper.delete(diaryQueryWrapper);

        LambdaQueryWrapper<EmailVerificationCode> emailCodeQueryWrapper = new LambdaQueryWrapper<>();
        emailCodeQueryWrapper.eq(EmailVerificationCode::getEmail, user.getEmail());
        emailVerificationCodeMapper.delete(emailCodeQueryWrapper);

        userMapper.deleteById(userId);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                imageStoragePaths.forEach(imageStorageService::delete);
            }
        });
    }

    private void checkUserAvailable(User user) {
        if (user == null) {
            throw new RuntimeException("用户不存在");
        }

        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new RuntimeException("账号状态异常");
        }
    }

    private UserVO toUserVO(User user) {
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setEmail(user.getEmail());
        userVO.setNickname(user.getNickname());
        userVO.setAvatarUrl(user.getAvatarUrl());
        return userVO;
    }

    private int countPlainTextCharacters(String content) {
        if (!StringUtils.hasText(content)) {
            return 0;
        }

        String plainText = content
                .replaceAll("(?i)<img\\b[^>]*>", "")
                .replaceAll("!\\[图片]\\(moodecho-image:\\d+\\)", "")
                .replaceAll("moodecho-image:\\d+", "")
                .replaceAll("<[^>]+>", "")
                .replace("&nbsp;", " ")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", "");

        return plainText.codePointCount(0, plainText.length());
    }
}
