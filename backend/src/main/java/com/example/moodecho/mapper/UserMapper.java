package com.example.moodecho.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.moodecho.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
    /*
    * 继承 BaseMapper<User>，已经拥有通用的 CRUD 方法
    *
    * insert(user);        // 新增用户
    * selectById(id);      // 根据 id 查询
    * selectList(null);    // 查询全部
    * updateById(user);    // 根据 id 修改
    * deleteById(id);      // 根据 id 删除
     */


}
