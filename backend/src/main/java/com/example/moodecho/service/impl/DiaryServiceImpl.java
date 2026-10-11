package com.example.moodecho.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.moodecho.dto.CreateDiaryDraftRequest;
import com.example.moodecho.dto.CreateDiaryRequest;
import com.example.moodecho.dto.UpdateDiaryRequest;
import com.example.moodecho.entity.Diary;
import com.example.moodecho.entity.DiaryImage;
import com.example.moodecho.mapper.DiaryImageMapper;
import com.example.moodecho.mapper.DiaryMapper;
import com.example.moodecho.service.DiaryEmbeddingService;
import com.example.moodecho.service.DiaryEmbeddingJobService;
import com.example.moodecho.service.DiaryImageService;
import com.example.moodecho.service.DiaryService;
import com.example.moodecho.service.ResonancePostService;
import com.example.moodecho.vo.DiaryVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * 私密日记业务实现类
 * 负责日记的增删改查、标题归一化、逻辑删除过滤，以及所有日记操作的用户归属校验。
 */
@Service
public class DiaryServiceImpl implements DiaryService {

    private static final Logger log = LoggerFactory.getLogger(DiaryServiceImpl.class);

    private final DiaryMapper diaryMapper;
    private final DiaryImageMapper diaryImageMapper;
    private final DiaryImageService diaryImageService;
    private final DiaryEmbeddingService diaryEmbeddingService;
    private final DiaryEmbeddingJobService embeddingJobs;
    private final ResonancePostService resonancePostService;

    public DiaryServiceImpl(
            DiaryMapper diaryMapper,
            DiaryImageMapper diaryImageMapper,
            DiaryImageService diaryImageService,
            DiaryEmbeddingService diaryEmbeddingService,
            DiaryEmbeddingJobService embeddingJobs,
            ResonancePostService resonancePostService
    ) {
        this.diaryMapper = diaryMapper;
        this.diaryImageMapper = diaryImageMapper;
        this.diaryImageService = diaryImageService;
        this.diaryEmbeddingService = diaryEmbeddingService;
        this.embeddingJobs = embeddingJobs;
        this.resonancePostService = resonancePostService;
    }

    @Override
    @Transactional
    public DiaryVO createDiary(Long currentUserId, CreateDiaryRequest request) {
        validateDiaryNotBlank(null, request.getTitle(), request.getContent());

        Diary diary = new Diary();
        diary.setUserId(currentUserId);
        diary.setTitle(normalizeTitle(request.getTitle()));
        diary.setContent(normalizeContent(request.getContent()));
        diary.setRecordDate(request.getRecordDate());
        diary.setDeleted(0);
        diary.setStatus(1);

        diaryMapper.insert(diary);
        embeddingJobs.enqueue(currentUserId, diary.getId());

        return toDiaryVO(diaryMapper.selectById(diary.getId()));
    }

    @Override
    public DiaryVO createDraftDiary(Long currentUserId, CreateDiaryDraftRequest request) {
        Diary diary = new Diary();
        diary.setUserId(currentUserId);
        diary.setTitle(normalizeTitle(request.getTitle()));
        diary.setContent(request.getContent() == null ? "" : request.getContent());
        diary.setRecordDate(request.getRecordDate());
        diary.setDeleted(0);
        diary.setStatus(0);

        diaryMapper.insert(diary);

        return toDiaryVO(diaryMapper.selectById(diary.getId()));
    }

    @Override
    @Transactional
    public DiaryVO publishDraftDiary(Long currentUserId, Long diaryId, UpdateDiaryRequest request) {
        Diary diary = getOwnDraftOrThrow(currentUserId, diaryId);

        validateDiaryNotBlank(diaryId, request.getTitle(), request.getContent());

        diary.setTitle(normalizeTitle(request.getTitle()));
        diary.setContent(normalizeContent(request.getContent()));
        diary.setRecordDate(request.getRecordDate());
        diary.setStatus(1);

        diaryMapper.updateById(diary);
        embeddingJobs.enqueue(currentUserId, diaryId);

        return toDiaryVO(diaryMapper.selectById(diaryId));
    }

    @Override
    @Transactional
    public void discardDraftDiary(Long currentUserId, Long diaryId) {
        Diary diary = diaryMapper.selectById(diaryId);

        if (diary == null || Integer.valueOf(1).equals(diary.getDeleted())) {
            return;
        }

        if (!diary.getUserId().equals(currentUserId)) {
            throw new RuntimeException("无权操作这篇日记");
        }

        if (!Integer.valueOf(0).equals(diary.getStatus())) {
            throw new RuntimeException("只能丢弃草稿日记");
        }

        diary.setDeleted(1);
        diaryMapper.updateById(diary);
        markDiaryImagesDeleted(diaryId);
    }

    @Override
    public List<DiaryVO> listMyDiaries(Long currentUserId) {
        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getUserId, currentUserId)
                .eq(Diary::getDeleted, 0)
                .eq(Diary::getStatus, 1)
                .orderByDesc(Diary::getRecordDate)
                .orderByDesc(Diary::getUpdatedAt)
                .orderByDesc(Diary::getId);

        return diaryMapper.selectList(queryWrapper)
                .stream()
                .map(this::toDiaryVO)
                .toList();
    }

    @Override
    public List<DiaryVO> listMyDiariesByDate(Long currentUserId, LocalDate recordDate) {
        if (recordDate == null) {
            throw new RuntimeException("记录日期不能为空");
        }

        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getUserId, currentUserId)
                .eq(Diary::getDeleted, 0)
                .eq(Diary::getStatus, 1)
                .eq(Diary::getRecordDate, recordDate)
                .orderByDesc(Diary::getCreatedAt);

        return diaryMapper.selectList(queryWrapper)
                .stream()
                .map(this::toDiaryVO)
                .toList();
    }

    @Override
    public List<LocalDate> listRecordedDates(Long currentUserId, Integer year, Integer month) {
        if (year == null || month == null) {
            throw new RuntimeException("年份和月份不能为空");
        }

        if (month < 1 || month > 12) {
            throw new RuntimeException("月份必须在 1 到 12 之间");
        }

        LocalDate startDate = LocalDate.of(year, month, 1);
        LocalDate endDate = startDate.withDayOfMonth(startDate.lengthOfMonth());

        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getUserId, currentUserId)
                .eq(Diary::getDeleted, 0)
                .eq(Diary::getStatus, 1)
                .ge(Diary::getRecordDate, startDate)
                .le(Diary::getRecordDate, endDate)
                .select(Diary::getRecordDate)
                .groupBy(Diary::getRecordDate)
                .orderByAsc(Diary::getRecordDate);

        return diaryMapper.selectList(queryWrapper)
                .stream()
                .map(Diary::getRecordDate)
                .toList();
    }

    @Override
    public List<DiaryVO> searchMyDiaries(Long currentUserId, String keyword) {
        if (keyword == null || keyword.trim().isEmpty()) {
            return List.of();
        }

        String searchKeyword = keyword.trim();

        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getUserId, currentUserId)
                .eq(Diary::getDeleted, 0)
                .eq(Diary::getStatus, 1)
                .and(wrapper -> wrapper
                        .like(Diary::getTitle, searchKeyword)
                        .or()
                        .like(Diary::getContent, searchKeyword))
                .orderByDesc(Diary::getRecordDate)
                .orderByDesc(Diary::getCreatedAt)
                .orderByDesc(Diary::getId);

        return diaryMapper.selectList(queryWrapper)
                .stream()
                .map(this::toDiaryVO)
                .toList();
    }

    @Override
    public DiaryVO getDiaryDetail(Long currentUserId, Long diaryId) {
        return toDiaryVO(getOwnDiaryOrThrow(currentUserId, diaryId));
    }

    @Override
    @Transactional
    public DiaryVO updateDiary(Long currentUserId, Long diaryId, UpdateDiaryRequest request) {
        Diary diary = getOwnDiaryOrThrow(currentUserId, diaryId);
        validateDiaryNotBlank(diaryId, request.getTitle(), request.getContent());

        diary.setTitle(normalizeTitle(request.getTitle()));
        diary.setContent(normalizeContent(request.getContent()));
        diary.setRecordDate(request.getRecordDate());

        diaryMapper.updateById(diary);
        embeddingJobs.enqueue(currentUserId, diaryId);

        return toDiaryVO(diaryMapper.selectById(diaryId));
    }

    @Override
    @Transactional
    public void deleteDiary(Long currentUserId, Long diaryId) {
        Diary diary = getOwnDiaryOrThrow(currentUserId, diaryId);
        diary.setDeleted(1);

        diaryMapper.updateById(diary);
        markDiaryImagesDeleted(diaryId);
        resonancePostService.withdrawPostsByDiaryId(currentUserId, diaryId);
        deleteDiaryEmbeddingQuietly(diaryId);
        embeddingJobs.cancel(currentUserId, diaryId);
    }

    private void deleteDiaryEmbeddingQuietly(Long diaryId) {
        try {
            diaryEmbeddingService.deleteDiaryEmbeddingByDiaryId(diaryId);
        } catch (RuntimeException e) {
            log.warn("日记语义向量删除失败，diaryId={}，原因={}", diaryId, e.getMessage());
        }
    }

    private void markDiaryImagesDeleted(Long diaryId) {
        LambdaQueryWrapper<DiaryImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryImage::getDiaryId, diaryId)
                .eq(DiaryImage::getDeleted, 0);

        List<DiaryImage> images = diaryImageMapper.selectList(queryWrapper);
        for (DiaryImage image : images) {
            image.setDeleted(1);
            diaryImageMapper.updateById(image);
        }
    }

    private void validateDiaryNotBlank(Long diaryId, String title, String content) {
        if (
                hasText(title)
                        || hasText(stripHtml(content))
                        || (hasImageReference(content) && hasActiveDiaryImages(diaryId))
        ) {
            return;
        }

        throw new RuntimeException("请写下一点内容，或添加一张图片");
    }

    private boolean hasActiveDiaryImages(Long diaryId) {
        if (diaryId == null) {
            return false;
        }

        LambdaQueryWrapper<DiaryImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryImage::getDiaryId, diaryId)
                .eq(DiaryImage::getDeleted, 0);

        return diaryImageMapper.selectCount(queryWrapper) > 0;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean hasImageReference(String content) {
        return content != null
                && (content.contains("<img") || content.contains("moodecho-image:"));
    }

    private String stripHtml(String content) {
        if (content == null) {
            return "";
        }

        return content
                .replaceAll("<[^>]*>", "")
                .replace("&nbsp;", " ")
                .trim();
    }

    private String normalizeContent(String content) {
        return content == null ? "" : content;
    }

    private Diary getOwnDiaryOrThrow(Long currentUserId, Long diaryId) {
        if (diaryId == null) {
            throw new RuntimeException("日记ID不能为空");
        }

        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getId, diaryId)
                .eq(Diary::getUserId, currentUserId)
                .eq(Diary::getDeleted, 0)
                .eq(Diary::getStatus, 1);

        Diary diary = diaryMapper.selectOne(queryWrapper);
        if (diary == null) {
            throw new RuntimeException("日记不存在或无权访问");
        }

        return diary;
    }

    private Diary getOwnDraftOrThrow(Long currentUserId, Long diaryId) {
        if (diaryId == null) {
            throw new RuntimeException("日记ID不能为空");
        }

        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getId, diaryId)
                .eq(Diary::getUserId, currentUserId)
                .eq(Diary::getDeleted, 0)
                .eq(Diary::getStatus, 0);

        Diary diary = diaryMapper.selectOne(queryWrapper);
        if (diary == null) {
            throw new RuntimeException("草稿不存在或无权访问");
        }

        return diary;
    }

    private DiaryVO toDiaryVO(Diary diary) {
        DiaryVO diaryVO = new DiaryVO();
        diaryVO.setId(diary.getId());
        diaryVO.setTitle(diary.getTitle());
        diaryVO.setContent(diary.getContent());
        diaryVO.setRecordDate(diary.getRecordDate());
        diaryVO.setCreatedAt(diary.getCreatedAt());
        diaryVO.setUpdatedAt(diary.getUpdatedAt());
        diaryVO.setImages(diaryImageService.listDiaryImages(diary.getUserId(), diary.getId()));
        return diaryVO;
    }

    private String normalizeTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            return null;
        }

        return title.trim();
    }
}
