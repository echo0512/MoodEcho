package com.example.moodecho.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.moodecho.entity.Diary;
import org.apache.ibatis.annotations.Mapper;

/**
 * 私密日记数据访问接口
 * 继承 MyBatis-Plus BaseMapper，提供 diary 表的基础增删改查能力。
 */
@Mapper
public interface DiaryMapper extends BaseMapper<Diary> {
}
