package com.example.moodecho.controller;

import com.example.moodecho.common.ApiResponse;
import com.example.moodecho.service.DiaryImageService;
import com.example.moodecho.util.AuthUtil;
import com.example.moodecho.vo.DiaryImageVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 日记图片接口控制器
 * 提供日记图片上传、列表查询、图片文件查看和逻辑删除接口，并统一校验当前登录用户身份。
 */
@RestController
public class DiaryImageController {

    private final DiaryImageService diaryImageService;
    private final AuthUtil authUtil;

    public DiaryImageController(DiaryImageService diaryImageService, AuthUtil authUtil) {
        this.diaryImageService = diaryImageService;
        this.authUtil = authUtil;
    }

    /**
     * 上传某篇日记的图片
     */
    @PostMapping("/api/diaries/{diaryId}/images")
    public ApiResponse<DiaryImageVO> uploadDiaryImage(
            HttpServletRequest request,
            @PathVariable Long diaryId,
            @RequestParam("file") MultipartFile file
    ) {
        Long userId = authUtil.getCurrentUserId(request);
        return ApiResponse.success(diaryImageService.uploadDiaryImage(userId, diaryId, file));
    }

    /**
     * 查询某篇日记的图片列表
     */
    @GetMapping("/api/diaries/{diaryId}/images")
    public ApiResponse<List<DiaryImageVO>> listDiaryImages(
            HttpServletRequest request,
            @PathVariable Long diaryId
    ) {
        Long userId = authUtil.getCurrentUserId(request);
        return ApiResponse.success(diaryImageService.listDiaryImages(userId, diaryId));
    }

    /**
     * 查看图片文件
     *
     * 注意：如果前端使用 Authorization 请求头传 JWT，直接用 img src 不会自动携带该请求头。
     * 后续前端可用 axios 请求 blob，再转成 objectURL 展示。
     */
    @GetMapping("/api/diary-images/{imageId}/view")
    public ResponseEntity<Resource> viewDiaryImage(
            HttpServletRequest request,
            @PathVariable Long imageId
    ) {
        Long userId = authUtil.getCurrentUserId(request);
        Resource resource = diaryImageService.loadDiaryImageResource(userId, imageId);
        String contentType = diaryImageService.getImageContentType(userId, imageId);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    /**
     * 删除图片
     */
    @DeleteMapping("/api/diary-images/{imageId}")
    public ApiResponse<Void> deleteDiaryImage(
            HttpServletRequest request,
            @PathVariable Long imageId
    ) {
        Long userId = authUtil.getCurrentUserId(request);
        diaryImageService.deleteDiaryImage(userId, imageId);
        return ApiResponse.success(null);
    }
}
