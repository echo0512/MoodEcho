package com.example.moodecho.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.moodecho.entity.DiaryEmbedding;
import org.apache.ibatis.annotations.Mapper;

/**
 * 日记语义向量数据访问接口。
 */
@Mapper
public interface DiaryEmbeddingMapper extends BaseMapper<DiaryEmbedding> {
}
