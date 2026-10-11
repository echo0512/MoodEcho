package com.example.moodecho.controller;

import com.example.moodecho.common.ApiResponse;
import com.example.moodecho.dto.CreateResonancePostRequest;
import com.example.moodecho.service.ResonancePostService;
import com.example.moodecho.util.AuthUtil;
import com.example.moodecho.vo.MyResonancePostVO;
import com.example.moodecho.vo.ResonancePlazaPostVO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 匿名共鸣投递接口控制器。
 */
@RestController
@RequestMapping("/api/resonance-posts")
public class ResonancePostController {

    private final ResonancePostService resonancePostService;
    private final AuthUtil authUtil;

    public ResonancePostController(
            ResonancePostService resonancePostService,
            AuthUtil authUtil
    ) {
        this.resonancePostService = resonancePostService;
        this.authUtil = authUtil;
    }

    @PostMapping
    public ApiResponse<ResonancePlazaPostVO> createPost(
            HttpServletRequest httpServletRequest,
            @Valid @RequestBody CreateResonancePostRequest request
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(resonancePostService.createPost(currentUserId, request));
    }

    @GetMapping("/plaza")
    public ApiResponse<List<ResonancePlazaPostVO>> listRandomPlazaPosts(
            HttpServletRequest httpServletRequest,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String excludeIds
    ) {
        authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(resonancePostService.listRandomPlazaPosts(size, excludeIds));
    }

    @GetMapping("/plaza/topic/{topicId}")
    public ApiResponse<List<ResonancePlazaPostVO>> listRandomTopicPosts(
            HttpServletRequest httpServletRequest,
            @PathVariable Long topicId,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) String excludeIds
    ) {
        authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(resonancePostService.listRandomTopicPosts(topicId, size, excludeIds));
    }

    @GetMapping("/my")
    public ApiResponse<List<MyResonancePostVO>> listMyPublishedPosts(HttpServletRequest httpServletRequest) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(resonancePostService.listMyPublishedPosts(currentUserId));
    }

    @PutMapping("/{id}/withdraw")
    public ApiResponse<Void> withdrawPost(
            HttpServletRequest httpServletRequest,
            @PathVariable Long id
    ) {
        Long currentUserId = authUtil.getCurrentUserId(httpServletRequest);
        resonancePostService.withdrawPost(currentUserId, id);
        return ApiResponse.success(null);
    }
}
