package com.example.moodecho.controller;

import com.example.moodecho.common.ApiResponse;
import com.example.moodecho.dto.CreateDiaryDraftRequest;
import com.example.moodecho.dto.CreateDiaryRequest;
import com.example.moodecho.dto.SemanticSearchRequest;
import com.example.moodecho.dto.UpdateDiaryRequest;
import com.example.moodecho.service.DiaryEmbeddingService;
import com.example.moodecho.service.DiaryEmbeddingJobService;
import com.example.moodecho.vo.SemanticIndexStatusVO;
import com.example.moodecho.service.DiaryService;
import com.example.moodecho.util.AuthUtil;
import com.example.moodecho.vo.DiaryVO;
import com.example.moodecho.vo.SemanticDiaryResultVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 私密日记接口控制器
 * 暴露日记创建、列表、详情、编辑和删除接口，并统一从请求中的 JWT 解析当前登录用户。
 */
@RestController
@RequestMapping("/api/diaries")
public class DiaryController {

    private final DiaryService diaryService;
    private final DiaryEmbeddingService diaryEmbeddingService;
    private final AuthUtil authUtil;
    private final DiaryEmbeddingJobService embeddingJobs;

    public DiaryController(
            DiaryService diaryService,
            DiaryEmbeddingService diaryEmbeddingService,
            AuthUtil authUtil,
            DiaryEmbeddingJobService embeddingJobs
    ) {
        this.diaryService = diaryService;
        this.diaryEmbeddingService = diaryEmbeddingService;
        this.authUtil = authUtil;
        this.embeddingJobs = embeddingJobs;
    }

    @PostMapping
    public ApiResponse<DiaryVO> createDiary(
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody CreateDiaryRequest request
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryService.createDiary(currentUserId, request));
    }

    @PostMapping("/draft")
    public ApiResponse<DiaryVO> createDraftDiary(
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody CreateDiaryDraftRequest request
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryService.createDraftDiary(currentUserId, request));
    }

    @GetMapping
    public ApiResponse<List<DiaryVO>> listMyDiaries(HttpServletRequest httpServletRequest) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryService.listMyDiaries(currentUserId));
    }

    @GetMapping("/by-date")
    public ApiResponse<List<DiaryVO>> listMyDiariesByDate(
            HttpServletRequest httpServletRequest,
            @RequestParam @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate recordDate
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryService.listMyDiariesByDate(currentUserId, recordDate));
    }

    @GetMapping("/recorded-dates")
    public ApiResponse<List<LocalDate>> listRecordedDates(
            HttpServletRequest httpServletRequest,
            @RequestParam Integer year,
            @RequestParam Integer month
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryService.listRecordedDates(currentUserId, year, month));
    }

    @GetMapping("/search")
    public ApiResponse<List<DiaryVO>> searchDiaries(
            HttpServletRequest httpServletRequest,
            @RequestParam String keyword
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryService.searchMyDiaries(currentUserId, keyword));
    }

    @PostMapping("/semantic-search")
    public ApiResponse<List<SemanticDiaryResultVO>> semanticSearchDiaries(
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody SemanticSearchRequest request
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryEmbeddingService.semanticSearch(
                currentUserId,
                request.getQuery(),
                request.getLimit()
        ));
    }

    @PostMapping("/semantic-rebuild")
    public ApiResponse<Integer> rebuildSemanticEmbeddings(HttpServletRequest httpServletRequest) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(embeddingJobs.enqueueAll(currentUserId));
    }

    @GetMapping("/semantic-status")
    public ApiResponse<SemanticIndexStatusVO> semanticIndexStatus(HttpServletRequest request) {
        return ApiResponse.success(embeddingJobs.status(authUtil.getCurrentUserId(request)));
    }

    @GetMapping("/{id}")
    public ApiResponse<DiaryVO> getDiaryDetail(
            HttpServletRequest httpServletRequest,
            @PathVariable Long id
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryService.getDiaryDetail(currentUserId, id));
    }

    @GetMapping("/{id}/related")
    public ApiResponse<List<SemanticDiaryResultVO>> getRelatedDiaries(
            HttpServletRequest httpServletRequest,
            @PathVariable Long id,
            @RequestParam(required = false) Integer limit
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryEmbeddingService.findRelatedDiaries(currentUserId, id, limit));
    }

    @PutMapping("/{id}")
    public ApiResponse<DiaryVO> updateDiary(
            HttpServletRequest httpServletRequest,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDiaryRequest request
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryService.updateDiary(currentUserId, id, request));
    }

    @PutMapping("/{id}/publish")
    public ApiResponse<DiaryVO> publishDraftDiary(
            HttpServletRequest httpServletRequest,
            @PathVariable Long id,
            @Valid @RequestBody UpdateDiaryRequest request
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(diaryService.publishDraftDiary(currentUserId, id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> deleteDiary(
            HttpServletRequest httpServletRequest,
            @PathVariable Long id
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        diaryService.deleteDiary(currentUserId, id);
        return ApiResponse.success(null);
    }

    @DeleteMapping("/{id}/draft")
    public ApiResponse<Void> discardDraftDiary(
            HttpServletRequest httpServletRequest,
            @PathVariable Long id
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        diaryService.discardDraftDiary(currentUserId, id);
        return ApiResponse.success(null);
    }
}
