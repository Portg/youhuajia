package com.youhua.debt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youhua.debt.entity.Debt;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface DebtMapper extends BaseMapper<Debt> {
}
