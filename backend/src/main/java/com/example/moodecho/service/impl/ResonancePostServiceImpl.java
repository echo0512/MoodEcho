package com.example.moodecho.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.example.moodecho.dto.CreateResonancePostRequest;
import com.example.moodecho.entity.Diary;
import com.example.moodecho.entity.ResonancePost;
import com.example.moodecho.entity.ResonanceTopic;
import com.example.moodecho.mapper.DiaryMapper;
import com.example.moodecho.mapper.ResonancePostMapper;
import com.example.moodecho.mapper.ResonanceTopicMapper;
import com.example.moodecho.service.ResonancePostService;
import com.example.moodecho.util.TextCleanUtils;
import com.example.moodecho.vo.MyResonancePostVO;
import com.example.moodecho.vo.ResonancePlazaPostVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 匿名共鸣投递业务实现类。
 */
@Service
public class ResonancePostServiceImpl implements ResonancePostService {

    private static final int STATUS_PUBLISHED = 1;
    private static final int STATUS_WITHDRAWN = 2;
    private static final int MIN_CONTENT_LENGTH = 10;
    private static final int MAX_CONTENT_LENGTH = 500;
    private static final int DEFAULT_RANDOM_SIZE = 9;
    private static final int MAX_RANDOM_SIZE = 50;

    private final ResonancePostMapper resonancePostMapper;
    private final ResonanceTopicMapper resonanceTopicMapper;
    private final DiaryMapper diaryMapper;

    public ResonancePostServiceImpl(
            ResonancePostMapper resonancePostMapper,
            ResonanceTopicMapper resonanceTopicMapper,
            DiaryMapper diaryMapper
    ) {
        this.resonancePostMapper = resonancePostMapper;
        this.resonanceTopicMapper = resonanceTopicMapper;
        this.diaryMapper = diaryMapper;
    }

    @Override
    @Transactional
    public ResonancePlazaPostVO createPost(Long currentUserId, CreateResonancePostRequest request) {
        if (currentUserId == null) {
            throw new RuntimeException("未登录或登录状态已失效");
        }

        Long diaryId = request.getDiaryId();
        if (diaryId != null) {
            checkDiaryOwner(currentUserId, diaryId);
        }

        ResonanceTopic topic = null;
        Long topicId = request.getTopicId();
        if (topicId != null) {
            topic = getEnabledTopicOrThrow(topicId);
        }

        String cleanContent = cleanAndCheckContent(request.getContent());

        ResonancePost post = new ResonancePost();
        post.setUserId(currentUserId);
        post.setDiaryId(diaryId);
        post.setTopicId(topicId);
        post.setContent(cleanContent);
        post.setStatus(STATUS_PUBLISHED);

        resonancePostMapper.insert(post);

        return toPlazaVO(post, topic == null ? null : topic.getName());
    }

    @Override
    public List<ResonancePlazaPostVO> listRandomPlazaPosts(Integer size, String excludeIds) {
        int limit = normalizeSize(size);
        List<Long> excludedPostIds = parseExcludeIds(excludeIds);

        LambdaQueryWrapper<ResonancePost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ResonancePost::getStatus, STATUS_PUBLISHED);
        if (!excludedPostIds.isEmpty()) {
            queryWrapper.notIn(ResonancePost::getId, excludedPostIds);
        }

        // ORDER BY RAND() 在数据量大时性能会变差，后续可优化为随机ID、随机偏移量或预生成随机权重。
        queryWrapper.last("ORDER BY RAND() LIMIT " + limit);

        return toPlazaVOList(resonancePostMapper.selectList(queryWrapper));
    }

    @Override
    public List<ResonancePlazaPostVO> listRandomTopicPosts(Long topicId, Integer size, String excludeIds) {
        if (topicId == null) {
            throw new RuntimeException("话题ID不能为空");
        }
        getEnabledTopicOrThrow(topicId);

        int limit = normalizeSize(size);
        List<Long> excludedPostIds = parseExcludeIds(excludeIds);

        LambdaQueryWrapper<ResonancePost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ResonancePost::getStatus, STATUS_PUBLISHED)
                .eq(ResonancePost::getTopicId, topicId);
        if (!excludedPostIds.isEmpty()) {
            queryWrapper.notIn(ResonancePost::getId, excludedPostIds);
        }

        // ORDER BY RAND() 在数据量大时性能会变差，后续可优化为随机ID、随机偏移量或预生成随机权重。
        queryWrapper.last("ORDER BY RAND() LIMIT " + limit);

        return toPlazaVOList(resonancePostMapper.selectList(queryWrapper));
    }

    @Override
    public List<MyResonancePostVO> listMyPublishedPosts(Long currentUserId) {
        LambdaQueryWrapper<ResonancePost> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ResonancePost::getUserId, currentUserId)
                .eq(ResonancePost::getStatus, STATUS_PUBLISHED)
                .orderByDesc(ResonancePost::getCreatedAt)
                .orderByDesc(ResonancePost::getId);

        List<ResonancePost> posts = resonancePostMapper.selectList(queryWrapper);
        Map<Long, ResonanceTopic> topicMap = buildTopicMap(posts);

        return posts.stream()
                .map(post -> toMyVO(post, findTopic(topicMap, post)))
                .toList();
    }

    @Override
    @Transactional
    public void withdrawPost(Long currentUserId, Long postId) {
        if (postId == null) {
            throw new RuntimeException("投递ID不能为空");
        }

        ResonancePost post = resonancePostMapper.selectById(postId);
        if (post == null) {
            throw new RuntimeException("投递不存在");
        }

        if (!post.getUserId().equals(currentUserId)) {
            throw new RuntimeException("无权操作这条投递");
        }

        if (Integer.valueOf(STATUS_WITHDRAWN).equals(post.getStatus())) {
            return;
        }

        post.setStatus(STATUS_WITHDRAWN);
        post.setWithdrawnAt(LocalDateTime.now());
        resonancePostMapper.updateById(post);
    }

    @Override
    @Transactional
    public void withdrawPostsByDiaryId(Long userId, Long diaryId) {
        if (userId == null || diaryId == null) {
            return;
        }

        LambdaUpdateWrapper<ResonancePost> updateWrapper = new LambdaUpdateWrapper<>();
        updateWrapper.eq(ResonancePost::getUserId, userId)
                .eq(ResonancePost::getDiaryId, diaryId)
                .eq(ResonancePost::getStatus, STATUS_PUBLISHED)
                .set(ResonancePost::getStatus, STATUS_WITHDRAWN)
                .set(ResonancePost::getWithdrawnAt, LocalDateTime.now());

        resonancePostMapper.update(updateWrapper);
    }

    private void checkDiaryOwner(Long currentUserId, Long diaryId) {
        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getId, diaryId)
                .eq(Diary::getUserId, currentUserId)
                .eq(Diary::getDeleted, 0);

        Diary diary = diaryMapper.selectOne(queryWrapper);
        if (diary == null) {
            throw new RuntimeException("日记不存在或无权访问");
        }
    }

    private ResonanceTopic getEnabledTopicOrThrow(Long topicId) {
        LambdaQueryWrapper<ResonanceTopic> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ResonanceTopic::getId, topicId)
                .eq(ResonanceTopic::getEnabled, 1);

        ResonanceTopic topic = resonanceTopicMapper.selectOne(queryWrapper);
        if (topic == null) {
            throw new RuntimeException("话题不存在或已停用");
        }

        return topic;
    }

    private String cleanAndCheckContent(String content) {
        String cleanContent = TextCleanUtils.cleanDiaryContent(content);
        if (!StringUtils.hasText(cleanContent)) {
            throw new RuntimeException("投递内容不能为空");
        }

        int length = cleanContent.length();
        if (length < MIN_CONTENT_LENGTH || length > MAX_CONTENT_LENGTH) {
            throw new RuntimeException("投递内容长度必须在 10 到 500 字之间");
        }

        return cleanContent;
    }

    private int normalizeSize(Integer size) {
        if (size == null || size <= 0) {
            return DEFAULT_RANDOM_SIZE;
        }

        return Math.min(size, MAX_RANDOM_SIZE);
    }

    private List<Long> parseExcludeIds(String excludeIds) {
        if (!StringUtils.hasText(excludeIds)) {
            return List.of();
        }

        try {
            return Arrays.stream(excludeIds.split(","))
                    .map(String::trim)
                    .filter(StringUtils::hasText)
                    .map(Long::valueOf)
                    .filter(id -> id > 0)
                    .distinct()
                    .toList();
        } catch (NumberFormatException e) {
            throw new RuntimeException("excludeIds 参数格式不正确");
        }
    }

    private List<ResonancePlazaPostVO> toPlazaVOList(List<ResonancePost> posts) {
        Map<Long, ResonanceTopic> topicMap = buildTopicMap(posts);

        return posts.stream()
                .map(post -> toPlazaVO(post, findTopic(topicMap, post)))
                .toList();
    }

    private ResonanceTopic findTopic(Map<Long, ResonanceTopic> topicMap, ResonancePost post) {
        Long topicId = post.getTopicId();
        return topicId == null ? null : topicMap.get(topicId);
    }

    private Map<Long, ResonanceTopic> buildTopicMap(List<ResonancePost> posts) {
        List<Long> topicIds = posts.stream()
                .map(ResonancePost::getTopicId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();

        if (topicIds.isEmpty()) {
            return Map.of();
        }

        LambdaQueryWrapper<ResonanceTopic> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.in(ResonanceTopic::getId, topicIds);

        return resonanceTopicMapper.selectList(queryWrapper)
                .stream()
                .collect(Collectors.toMap(ResonanceTopic::getId, Function.identity()));
    }

    private ResonancePlazaPostVO toPlazaVO(ResonancePost post, ResonanceTopic topic) {
        return toPlazaVO(post, topic == null ? null : topic.getName());
    }

    private ResonancePlazaPostVO toPlazaVO(ResonancePost post, String topicName) {
        ResonancePlazaPostVO vo = new ResonancePlazaPostVO();
        vo.setId(post.getId());
        vo.setContent(post.getContent());
        vo.setTopicId(post.getTopicId());
        vo.setTopicName(topicName);
        return vo;
    }

    private MyResonancePostVO toMyVO(ResonancePost post, ResonanceTopic topic) {
        MyResonancePostVO vo = new MyResonancePostVO();
        vo.setId(post.getId());
        vo.setContent(post.getContent());
        vo.setTopicId(post.getTopicId());
        vo.setTopicName(topic == null ? null : topic.getName());
        vo.setCreatedAt(post.getCreatedAt());
        return vo;
    }
}
