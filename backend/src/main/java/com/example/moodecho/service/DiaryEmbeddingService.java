package com.example.moodecho.service;

import com.example.moodecho.vo.SemanticDiaryResultVO;

import java.util.List;

/**
 * 日记语义向量业务接口。
 */
public interface DiaryEmbeddingService {

    void upsertDiaryEmbedding(Long userId, Long diaryId);

    void deleteDiaryEmbeddingByDiaryId(Long diaryId);

    List<SemanticDiaryResultVO> semanticSearch(Long userId, String query, Integer limit);

    List<SemanticDiaryResultVO> findRelatedDiaries(Long userId, Long diaryId, Integer limit);

}
