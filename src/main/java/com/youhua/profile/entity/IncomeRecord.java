package com.youhua.profile.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.youhua.common.entity.BaseEntity;
import com.youhua.profile.enums.IncomeType;
import com.youhua.profile.enums.VerificationStatus;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@TableName("t_income_record")
public class IncomeRecord extends BaseEntity {

    private Long userId;

    private IncomeType incomeType;

    private BigDecimal amount;

    @TableField("`primary`")
    private Boolean primary;

    private VerificationStatus verificationStatus;
}
