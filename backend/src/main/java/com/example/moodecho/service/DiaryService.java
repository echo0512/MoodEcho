package com.example.moodecho.service;

import com.example.moodecho.dto.CreateDiaryRequest;
import com.example.moodecho.dto.CreateDiaryDraftRequest;
import com.example.moodecho.dto.UpdateDiaryRequest;
import com.example.moodecho.vo.DiaryVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 私密日记业务接口
 * 定义日记创建、列表查询、详情查询、编辑和逻辑删除能力，并要求调用方传入当前登录用户ID。
 */
public interface DiaryService {

    DiaryVO createDiary(Long currentUserId, CreateDiaryRequest request);

    DiaryVO createDraftDiary(Long currentUserId, CreateDiaryDraftRequest request);

    DiaryVO publishDraftDiary(Long currentUserId, Long diaryId, UpdateDiaryRequest request);

    void discardDraftDiary(Long currentUserId, Long diaryId);

    List<DiaryVO> listMyDiaries(Long currentUserId);

    List<DiaryVO> listMyDiariesByDate(Long currentUserId, LocalDate recordDate);

    List<LocalDate> listRecordedDates(Long currentUserId, Integer year, Integer month);

    List<DiaryVO> searchMyDiaries(Long currentUserId, String keyword);

    DiaryVO getDiaryDetail(Long currentUserId, Long diaryId);

    DiaryVO updateDiary(Long currentUserId, Long diaryId, UpdateDiaryRequest request);

    void deleteDiary(Long currentUserId, Long diaryId);
}
