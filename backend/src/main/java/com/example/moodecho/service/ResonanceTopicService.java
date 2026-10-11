package com.example.moodecho.service;

import com.example.moodecho.vo.ResonanceTopicVO;

import java.util.List;

/**
 * 共鸣话题业务接口。
 */
public interface ResonanceTopicService {

    List<ResonanceTopicVO> listEnabledTopics();
}
