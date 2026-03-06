package com.youhua.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youhua.auth.entity.User;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper extends BaseMapper<User> {
}
