package com.example.moodecho.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.moodecho.entity.ResonanceTopic;
import com.example.moodecho.mapper.ResonanceTopicMapper;
import com.example.moodecho.service.ResonanceTopicService;
import com.example.moodecho.vo.ResonanceTopicVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 共鸣话题业务实现类。
 */
@Service
public class ResonanceTopicServiceImpl implements ResonanceTopicService {

    private final ResonanceTopicMapper resonanceTopicMapper;

    public ResonanceTopicServiceImpl(ResonanceTopicMapper resonanceTopicMapper) {
        this.resonanceTopicMapper = resonanceTopicMapper;
    }

    @Override
    public List<ResonanceTopicVO> listEnabledTopics() {
        LambdaQueryWrapper<ResonanceTopic> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(ResonanceTopic::getEnabled, 1)
                .orderByAsc(ResonanceTopic::getSortOrder)
                .orderByAsc(ResonanceTopic::getId);

        return resonanceTopicMapper.selectList(queryWrapper)
                .stream()
                .map(this::toVO)
                .toList();
    }

    private ResonanceTopicVO toVO(ResonanceTopic topic) {
        ResonanceTopicVO vo = new ResonanceTopicVO();
        vo.setId(topic.getId());
        vo.setName(topic.getName());
        vo.setDescription(topic.getDescription());
        vo.setSortOrder(topic.getSortOrder());
        return vo;
    }
}
