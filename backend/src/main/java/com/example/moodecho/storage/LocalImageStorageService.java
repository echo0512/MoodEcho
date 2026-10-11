package com.example.moodecho.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.UUID;

/**
 * 本地图片存储实现。
 *
 * 当前开发阶段使用 uploads/diary-images 目录保存图片。
 */
@Service
public class LocalImageStorageService implements ImageStorageService {

    @Value("${moodecho.upload.diary-image-dir}")
    private String diaryImageDir;

    @Override
    public StoredFile store(MultipartFile file) {
        try {
            Path uploadDir = Paths.get(diaryImageDir).toAbsolutePath().normalize();
            Files.createDirectories(uploadDir);

            String originalName = StringUtils.cleanPath(file.getOriginalFilename());
            String extension = getFileExtension(originalName);
            String storedName = UUID.randomUUID() + extension;

            Path targetPath = uploadDir.resolve(storedName);
            file.transferTo(targetPath.toFile());

            return new StoredFile(
                    storedName,
                    targetPath.toString()
            );
        } catch (Exception e) {
            throw new RuntimeException("图片保存失败，请稍后再试");
        }
    }

    @Override
    public Resource load(String storagePath) {
        try {
            Path path = Paths.get(storagePath).toAbsolutePath().normalize();
            FileSystemResource resource = new FileSystemResource(path);

            if (!resource.exists() || !resource.isReadable()) {
                throw new RuntimeException("图片文件不存在或无法读取");
            }

            return resource;
        } catch (Exception e) {
            throw new RuntimeException("图片文件读取失败");
        }
    }

    @Override
    public void delete(String storagePath) {
        if (!StringUtils.hasText(storagePath)) {
            return;
        }

        try {
            Path path = Paths.get(storagePath).toAbsolutePath().normalize();
            Files.deleteIfExists(path);
        } catch (Exception e) {
            // 当前删除物理文件失败，不影响数据库逻辑删除。
            // 后续可以接日志系统记录。
        }
    }

    private String getFileExtension(String originalName) {
        if (!StringUtils.hasText(originalName) || !originalName.contains(".")) {
            return "";
        }

        return originalName.substring(originalName.lastIndexOf("."));
    }
}
