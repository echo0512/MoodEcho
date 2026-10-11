package com.example.moodecho.service;

import com.example.moodecho.vo.DiaryImageVO;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 日记图片业务接口
 * 定义图片上传、列表查询、文件加载、文件类型查询和逻辑删除能力。
 */
public interface DiaryImageService {

    /**
     * 上传日记图片
     */
    DiaryImageVO uploadDiaryImage(Long userId, Long diaryId, MultipartFile file);

    /**
     * 查询某篇日记的图片列表
     */
    List<DiaryImageVO> listDiaryImages(Long userId, Long diaryId);

    /**
     * 加载图片文件
     */
    Resource loadDiaryImageResource(Long userId, Long imageId);

    /**
     * 查询图片文件类型
     */
    String getImageContentType(Long userId, Long imageId);

    /**
     * 删除日记图片
     */
    void deleteDiaryImage(Long userId, Long imageId);
}
