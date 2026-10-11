package com.example.moodecho.controller;

import com.example.moodecho.common.ApiResponse;
import com.example.moodecho.service.ResonanceTopicService;
import com.example.moodecho.util.AuthUtil;
import com.example.moodecho.vo.ResonanceTopicVO;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 共鸣话题接口控制器。
 */
@RestController
@RequestMapping("/api/resonance-topics")
public class ResonanceTopicController {

    private final ResonanceTopicService resonanceTopicService;
    private final AuthUtil authUtil;

    public ResonanceTopicController(
            ResonanceTopicService resonanceTopicService,
            AuthUtil authUtil
    ) {
        this.resonanceTopicService = resonanceTopicService;
        this.authUtil = authUtil;
    }

    @GetMapping
    public ApiResponse<List<ResonanceTopicVO>> listEnabledTopics(HttpServletRequest httpServletRequest) {
        authUtil.getCurrentUserId(httpServletRequest);
        return ApiResponse.success(resonanceTopicService.listEnabledTopics());
    }
}
