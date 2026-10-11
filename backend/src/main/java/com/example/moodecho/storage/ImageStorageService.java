package com.example.moodecho.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

/**
 * 图片存储服务抽象。
 *
 * 业务层不关心图片到底存在本地、OSS、COS、S3 还是 MinIO。
 */
public interface ImageStorageService {

    /**
     * 保存图片文件。
     */
    StoredFile store(MultipartFile file);

    /**
     * 加载图片文件。
     */
    Resource load(String storagePath);

    /**
     * 删除图片文件。
     */
    void delete(String storagePath);
}
