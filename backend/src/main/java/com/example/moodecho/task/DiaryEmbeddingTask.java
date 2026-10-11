package com.example.moodecho.task;

import com.example.moodecho.entity.DiaryEmbeddingJob;
import com.example.moodecho.service.DiaryEmbeddingJobService;
import com.example.moodecho.service.DiaryEmbeddingService;
import com.example.moodecho.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DiaryEmbeddingTask {
    private static final Logger log = LoggerFactory.getLogger(DiaryEmbeddingTask.class);
    private final DiaryEmbeddingJobService jobs;
    private final DiaryEmbeddingService index;
    private final EmbeddingService embeddings;

    public DiaryEmbeddingTask(DiaryEmbeddingJobService jobs, DiaryEmbeddingService index, EmbeddingService embeddings) {
        this.jobs = jobs;
        this.index = index;
        this.embeddings = embeddings;
    }

    @Scheduled(fixedDelayString = "${moodecho.embedding.worker-delay-ms:1000}", initialDelay = 5000)
    public void processNext() {
        // 未配置服务时保留任务，不消耗重试次数。
        if (!embeddings.isConfigured()) return;
        DiaryEmbeddingJob job = jobs.claimNext();
        if (job == null) return;
        try {
            index.upsertDiaryEmbedding(job.getUserId(), job.getDiaryId());
            jobs.complete(job);
        } catch (RuntimeException e) {
            jobs.retry(job);
            log.warn("日记索引任务失败，diaryId={}，attempt={}，type={}",
                    job.getDiaryId(), job.getAttempts(), e.getClass().getSimpleName());
        }
    }
}
