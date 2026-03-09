package com.youhua.debt.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.youhua.common.annotation.AiGenerated;
import com.youhua.debt.entity.Debt;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.Map;

@Mapper
public interface DebtMapper extends BaseMapper<Debt> {

    /**
     * 聚合查询：一条 SQL 获取 totalCount / totalPrincipal / totalMonthlyPayment / confirmedCount
     * 替代原先 listDebts 中的全量 selectList 二次查询
     */
    @AiGenerated(reason = "聚合SQL，返回Map结构需运行时确认列名映射", generatedAt = "2026-03-09", reviewBy = "2026-Q2")
    @Select("""
            SELECT COUNT(*) AS total_count,
                   COALESCE(SUM(principal), 0) AS total_principal,
                   COALESCE(SUM(monthly_payment), 0) AS total_monthly_payment,
                   SUM(CASE WHEN status IN ('CONFIRMED', 'IN_PROFILE') THEN 1 ELSE 0 END) AS confirmed_count
            FROM t_debt
            WHERE user_id = #{userId} AND deleted = 0
            """)
    Map<String, Object> selectSummaryByUserId(@Param("userId") Long userId);
}

