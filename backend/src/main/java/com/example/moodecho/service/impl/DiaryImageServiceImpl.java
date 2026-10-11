package com.example.moodecho.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.moodecho.entity.Diary;
import com.example.moodecho.entity.DiaryImage;
import com.example.moodecho.mapper.DiaryImageMapper;
import com.example.moodecho.mapper.DiaryMapper;
import com.example.moodecho.service.DiaryImageService;
import com.example.moodecho.storage.ImageStorageService;
import com.example.moodecho.storage.StoredFile;
import com.example.moodecho.vo.DiaryImageVO;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 日记图片业务实现类
 * 负责校验日记归属、限制图片格式和数量、维护图片记录以及删除正文中的图片标记。
 */
@Service
public class DiaryImageServiceImpl implements DiaryImageService {

    private static final long MAX_IMAGE_SIZE = 5 * 1024 * 1024;

    private static final int MAX_IMAGE_COUNT_PER_DIARY = 9;

    private final DiaryImageMapper diaryImageMapper;
    private final DiaryMapper diaryMapper;
    private final ImageStorageService imageStorageService;

    public DiaryImageServiceImpl(
            DiaryImageMapper diaryImageMapper,
            DiaryMapper diaryMapper,
            ImageStorageService imageStorageService
    ) {
        this.diaryImageMapper = diaryImageMapper;
        this.diaryMapper = diaryMapper;
        this.imageStorageService = imageStorageService;
    }

    @Override
    public DiaryImageVO uploadDiaryImage(Long userId, Long diaryId, MultipartFile file) {
        Diary diary = checkDiaryOwner(userId, diaryId);

        if (file == null || file.isEmpty()) {
            throw new RuntimeException("请选择要上传的图片");
        }

        if (file.getSize() > MAX_IMAGE_SIZE) {
            throw new RuntimeException("单张图片不能超过 5MB");
        }

        String contentType = file.getContentType();
        if (!isAllowedImageType(contentType)) {
            throw new RuntimeException("只支持 JPG、PNG、WebP 格式图片");
        }

        Long imageCount = countActiveImages(diaryId);
        if (imageCount >= MAX_IMAGE_COUNT_PER_DIARY) {
            throw new RuntimeException("每篇日记最多上传 9 张图片");
        }

        String originalName = normalizeOriginalName(file.getOriginalFilename());
        StoredFile storedFile = imageStorageService.store(file);

        DiaryImage diaryImage = new DiaryImage();
        diaryImage.setDiaryId(diary.getId());
        diaryImage.setUserId(userId);
        diaryImage.setOriginalName(originalName);
        diaryImage.setStoredName(storedFile.getStoredName());
        diaryImage.setContentType(contentType);
        diaryImage.setFileSize(file.getSize());
        diaryImage.setStoragePath(storedFile.getStoragePath());
        diaryImage.setDeleted(0);

        diaryImageMapper.insert(diaryImage);

        return toVO(diaryImage);
    }

    @Override
    public List<DiaryImageVO> listDiaryImages(Long userId, Long diaryId) {
        checkDiaryOwner(userId, diaryId);

        LambdaQueryWrapper<DiaryImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryImage::getDiaryId, diaryId)
                .eq(DiaryImage::getUserId, userId)
                .eq(DiaryImage::getDeleted, 0)
                .orderByAsc(DiaryImage::getCreatedAt);

        return diaryImageMapper.selectList(queryWrapper)
                .stream()
                .map(this::toVO)
                .toList();
    }

    @Override
    public Resource loadDiaryImageResource(Long userId, Long imageId) {
        DiaryImage image = getActiveImageAndCheckOwner(userId, imageId);

        return imageStorageService.load(image.getStoragePath());
    }

    @Override
    public String getImageContentType(Long userId, Long imageId) {
        DiaryImage image = getActiveImageAndCheckOwner(userId, imageId);
        return image.getContentType();
    }

    @Override
    @Transactional
    public void deleteDiaryImage(Long userId, Long imageId) {
        DiaryImage image = getActiveImageAndCheckOwner(userId, imageId);

        image.setDeleted(1);
        diaryImageMapper.updateById(image);

        imageStorageService.delete(image.getStoragePath());

        Diary diary = diaryMapper.selectById(image.getDiaryId());
        if (diary != null && diary.getContent() != null) {
            diary.setContent(removeImageReference(diary.getContent(), image.getId()));
            diaryMapper.updateById(diary);
        }
    }

    private Diary checkDiaryOwner(Long userId, Long diaryId) {
        Diary diary = diaryMapper.selectById(diaryId);

        if (diary == null || Integer.valueOf(1).equals(diary.getDeleted())) {
            throw new RuntimeException("日记不存在");
        }

        if (!diary.getUserId().equals(userId)) {
            throw new RuntimeException("无权操作这篇日记");
        }

        return diary;
    }

    private DiaryImage getActiveImageAndCheckOwner(Long userId, Long imageId) {
        DiaryImage image = diaryImageMapper.selectById(imageId);

        if (image == null || Integer.valueOf(1).equals(image.getDeleted())) {
            throw new RuntimeException("图片不存在");
        }

        if (!image.getUserId().equals(userId)) {
            throw new RuntimeException("无权访问这张图片");
        }

        return image;
    }

    private Long countActiveImages(Long diaryId) {
        LambdaQueryWrapper<DiaryImage> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(DiaryImage::getDiaryId, diaryId)
                .eq(DiaryImage::getDeleted, 0);

        return diaryImageMapper.selectCount(queryWrapper);
    }

    private boolean isAllowedImageType(String contentType) {
        return "image/jpeg".equals(contentType)
                || "image/png".equals(contentType)
                || "image/webp".equals(contentType);
    }

    private String normalizeOriginalName(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            return "image";
        }

        return StringUtils.cleanPath(originalFilename);
    }

    private String removeImageReference(String content, Long imageId) {
        if (content == null) {
            return null;
        }

        String markdownTag = buildMarkdownTag(imageId);
        String htmlImgPattern = "<img[^>]*data-image-id=[\"']"
                + imageId
                + "[\"'][^>]*>";

        return content
                .replace(markdownTag, "")
                .replaceAll(htmlImgPattern, "");
    }

    private DiaryImageVO toVO(DiaryImage image) {
        DiaryImageVO vo = new DiaryImageVO();
        vo.setId(image.getId());
        vo.setDiaryId(image.getDiaryId());
        vo.setOriginalName(image.getOriginalName());
        vo.setContentType(image.getContentType());
        vo.setFileSize(image.getFileSize());
        vo.setMarkdownTag(buildMarkdownTag(image.getId()));
        vo.setViewUrl("/api/diary-images/" + image.getId() + "/view");
        vo.setCreatedAt(image.getCreatedAt());
        return vo;
    }

    private String buildMarkdownTag(Long imageId) {
        return "![图片](moodecho-image:" + imageId + ")";
    }
}
