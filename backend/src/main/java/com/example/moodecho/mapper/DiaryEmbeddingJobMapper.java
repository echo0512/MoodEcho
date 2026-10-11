package com.example.moodecho.mapper;

import com.example.moodecho.entity.DiaryEmbeddingJob;
import com.example.moodecho.vo.SemanticIndexStatusVO;
import org.apache.ibatis.annotations.*;

@Mapper
public interface DiaryEmbeddingJobMapper {
    // 与日记写入共用事务。修改会使旧任务的领取凭证失效，合并为最新版本。
    @Insert("""
            INSERT INTO diary_embedding_job (diary_id, user_id)
            SELECT id, user_id FROM diary
            WHERE id = #{diaryId} AND user_id = #{userId} AND deleted = 0 AND status = 1
            ON DUPLICATE KEY UPDATE revision = revision + 1, state = 'pending', attempts = 0,
              next_attempt_at = CURRENT_TIMESTAMP(6), lease_until = NULL, claim_token = NULL,
              error_code = NULL, updated_at = CURRENT_TIMESTAMP(6)
            """)
    int enqueue(@Param("userId") Long userId, @Param("diaryId") Long diaryId);

    @Insert("""
            INSERT INTO diary_embedding_job (diary_id, user_id)
            SELECT id, user_id FROM diary
            WHERE user_id = #{userId} AND deleted = 0 AND status = 1
            ORDER BY id
            ON DUPLICATE KEY UPDATE revision = revision + 1, state = 'pending', attempts = 0,
              next_attempt_at = CURRENT_TIMESTAMP(6), lease_until = NULL, claim_token = NULL,
              error_code = NULL, updated_at = CURRENT_TIMESTAMP(6)
            """)
    int enqueueAll(@Param("userId") Long userId);

    @Select("SELECT COUNT(*) FROM diary WHERE user_id = #{userId} AND deleted = 0 AND status = 1")
    int countDiaries(@Param("userId") Long userId);

    @Select("""
            SELECT diary_id, user_id, revision, attempts, claim_token FROM diary_embedding_job
            WHERE attempts < #{maxAttempts} AND (
              (state = 'pending' AND next_attempt_at <= CURRENT_TIMESTAMP(6)) OR
              (state = 'processing' AND lease_until <= CURRENT_TIMESTAMP(6)))
            ORDER BY next_attempt_at, diary_id LIMIT 1 FOR UPDATE SKIP LOCKED
            """)
    DiaryEmbeddingJob nextJob(@Param("maxAttempts") int maxAttempts);

    @Update("""
            UPDATE diary_embedding_job SET state = 'processing', attempts = attempts + 1,
              claim_token = #{token}, lease_until = TIMESTAMPADD(SECOND, #{leaseSeconds}, CURRENT_TIMESTAMP(6)),
              updated_at = CURRENT_TIMESTAMP(6)
            WHERE diary_id = #{diaryId} AND revision = #{revision}
            """)
    int claim(@Param("diaryId") Long diaryId, @Param("revision") Long revision,
              @Param("token") String token, @Param("leaseSeconds") int leaseSeconds);

    // revision + claim_token 双重校验：旧版本或已过期的 worker 不能覆盖新任务状态。
    @Update("""
            UPDATE diary_embedding_job SET state = #{state}, error_code = #{errorCode},
              next_attempt_at = TIMESTAMPADD(SECOND, #{delaySeconds}, CURRENT_TIMESTAMP(6)),
              claim_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP(6)
            WHERE diary_id = #{job.diaryId} AND revision = #{job.revision}
              AND claim_token = #{job.claimToken} AND state = 'processing'
            """)
    int finish(@Param("job") DiaryEmbeddingJob job, @Param("state") String state,
               @Param("errorCode") String errorCode, @Param("delaySeconds") int delaySeconds);

    @Update("""
            UPDATE diary_embedding_job SET state = 'failed', error_code = 'LEASE_EXPIRED',
              claim_token = NULL, lease_until = NULL, updated_at = CURRENT_TIMESTAMP(6)
            WHERE state = 'processing' AND lease_until <= CURRENT_TIMESTAMP(6) AND attempts >= #{maxAttempts}
            """)
    int failExhausted(@Param("maxAttempts") int maxAttempts);

    @Delete("DELETE FROM diary_embedding_job WHERE diary_id = #{diaryId} AND user_id = #{userId}")
    int cancel(@Param("userId") Long userId, @Param("diaryId") Long diaryId);

    @Select("""
            SELECT COALESCE(SUM(j.state = 'pending'), 0) AS pending,
              COALESCE(SUM(j.state = 'processing'), 0) AS processing,
              COALESCE(SUM(j.state = 'ready'), 0) AS ready,
              COALESCE(SUM(j.state = 'failed'), 0) AS failed,
              MAX(CASE WHEN j.state = 'ready' THEN j.updated_at END) AS last_completed_at
            FROM diary_embedding_job j JOIN diary d ON d.id = j.diary_id
            WHERE j.user_id = #{userId} AND d.user_id = #{userId} AND d.deleted = 0 AND d.status = 1
            """)
    SemanticIndexStatusVO status(@Param("userId") Long userId);
}
