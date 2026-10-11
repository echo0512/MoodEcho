package com.example.moodecho.entity;

import lombok.Data;

/** 持久化任务的领取快照；不保存日记正文。 */
@Data
public class DiaryEmbeddingJob {
    private Long diaryId;
    private Long userId;
    private Long revision;
    private Integer attempts;
    private String claimToken;
}
