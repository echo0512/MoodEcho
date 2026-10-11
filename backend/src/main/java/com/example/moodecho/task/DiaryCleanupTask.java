package com.example.moodecho.task;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.moodecho.entity.Diary;
import com.example.moodecho.entity.DiaryImage;
import com.example.moodecho.mapper.DiaryImageMapper;
import com.example.moodecho.mapper.DiaryMapper;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 清理长时间未发布的草稿日记及其图片记录。
 */
@Component
public class DiaryCleanupTask {

    private final DiaryMapper diaryMapper;
    private final DiaryImageMapper diaryImageMapper;

    public DiaryCleanupTask(DiaryMapper diaryMapper, DiaryImageMapper diaryImageMapper) {
        this.diaryMapper = diaryMapper;
        this.diaryImageMapper = diaryImageMapper;
    }

    @Scheduled(cron = "0 0 0 * * ?")
    @Transactional
    public void cleanOldDrafts() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);

        LambdaQueryWrapper<Diary> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Diary::getStatus, 0)
                .eq(Diary::getDeleted, 0)
                .lt(Diary::getCreatedAt, threshold);

        List<Diary> drafts = diaryMapper.selectList(queryWrapper);

        for (Diary draft : drafts) {
            draft.setDeleted(1);
            diaryMapper.updateById(draft);
            markDiaryImagesDeleted(draft.getId());
        }
    }

    private void markDiaryImagesDeleted(Long diaryId) {
        LambdaQueryWrapper<DiaryImage> imageQueryWrapper = new LambdaQueryWrapper<>();
        imageQueryWrapper.eq(DiaryImage::getDiaryId, diaryId)
                .eq(DiaryImage::getDeleted, 0);

        List<DiaryImage> images = diaryImageMapper.selectList(imageQueryWrapper);
        for (DiaryImage image : images) {
            image.setDeleted(1);
            diaryImageMapper.updateById(image);
        }
    }
}
