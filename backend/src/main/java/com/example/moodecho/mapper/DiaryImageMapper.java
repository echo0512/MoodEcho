package com.example.moodecho.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.moodecho.entity.DiaryImage;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日记图片 Mapper
 * 提供 diary_image 表的基础 CRUD 能力。
 */
@Mapper
public interface DiaryImageMapper extends BaseMapper<DiaryImage> {
}
