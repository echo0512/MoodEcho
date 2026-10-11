package com.example.moodecho.service;

import com.example.moodecho.dto.CreateResonancePostRequest;
import com.example.moodecho.vo.MyResonancePostVO;
import com.example.moodecho.vo.ResonancePlazaPostVO;

import java.util.List;

/**
 * 匿名共鸣投递业务接口。
 */
public interface ResonancePostService {

    ResonancePlazaPostVO createPost(Long currentUserId, CreateResonancePostRequest request);

    List<ResonancePlazaPostVO> listRandomPlazaPosts(Integer size, String excludeIds);

    List<ResonancePlazaPostVO> listRandomTopicPosts(Long topicId, Integer size, String excludeIds);

    List<MyResonancePostVO> listMyPublishedPosts(Long currentUserId);

    void withdrawPost(Long currentUserId, Long postId);

    void withdrawPostsByDiaryId(Long userId, Long diaryId);
}
