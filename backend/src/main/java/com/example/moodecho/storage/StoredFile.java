package com.example.moodecho.storage;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件保存后的结果。
 *
 * 当前本地存储时：
 * storagePath = 本地文件绝对路径
 * storedName = 服务器保存后的文件名
 *
 * 以后对象存储时：
 * storagePath 可以变成 objectKey，例如 diary-images/user-1/xxx.jpg
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class StoredFile {

    /**
     * 服务器保存后的文件名。
     */
    private String storedName;

    /**
     * 存储路径。
     * 本地存储时是文件路径；对象存储时可以是 objectKey。
     */
    private String storagePath;
}
