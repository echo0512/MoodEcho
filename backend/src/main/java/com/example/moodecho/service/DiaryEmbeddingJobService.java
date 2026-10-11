package com.example.moodecho.service;

import com.example.moodecho.entity.DiaryEmbeddingJob;
import com.example.moodecho.mapper.DiaryEmbeddingJobMapper;
import com.example.moodecho.vo.SemanticIndexStatusVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;

@Service
public class DiaryEmbeddingJobService {
    public static final int MAX_ATTEMPTS = 5;
    // 超时后允许重领；网络调用期间不持有数据库锁。
    private static final int LEASE_SECONDS = 1800;
    private final DiaryEmbeddingJobMapper mapper;
    private final EmbeddingService embeddings;
    private final TransactionTemplate transactions;

    public DiaryEmbeddingJobService(DiaryEmbeddingJobMapper mapper, EmbeddingService embeddings,
                                    PlatformTransactionManager transactionManager) {
        this.mapper = mapper;
        this.embeddings = embeddings;
        this.transactions = new TransactionTemplate(transactionManager);
    }

    public void enqueue(Long userId, Long diaryId) {
        mapper.enqueue(userId, diaryId);
    }

    @Transactional
    public int enqueueAll(Long userId) {
        mapper.enqueueAll(userId);
        return mapper.countDiaries(userId);
    }

    public void cancel(Long userId, Long diaryId) {
        mapper.cancel(userId, diaryId);
    }

    public SemanticIndexStatusVO status(Long userId) {
        SemanticIndexStatusVO status = mapper.status(userId);
        status.setAvailable(embeddings.isConfigured());
        return status;
    }

    public DiaryEmbeddingJob claimNext() {
        return transactions.execute(status -> {
            mapper.failExhausted(MAX_ATTEMPTS);
            DiaryEmbeddingJob job = mapper.nextJob(MAX_ATTEMPTS);
            if (job == null) return null;
            String token = UUID.randomUUID().toString();
            if (mapper.claim(job.getDiaryId(), job.getRevision(), token, LEASE_SECONDS) != 1) return null;
            job.setClaimToken(token);
            job.setAttempts(job.getAttempts() + 1);
            return job;
        });
    }

    public void complete(DiaryEmbeddingJob job) {
        mapper.finish(job, "ready", null, 0);
    }

    public void retry(DiaryEmbeddingJob job) {
        boolean exhausted = job.getAttempts() >= MAX_ATTEMPTS;
        int delay = Math.min(300, 15 * (1 << Math.min(4, Math.max(0, job.getAttempts() - 1))));
        mapper.finish(job, exhausted ? "failed" : "pending", "GENERATION_FAILED", delay);
    }
}
