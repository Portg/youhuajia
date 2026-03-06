package com.youhua.profile.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.youhua.common.entity.BaseEntity;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@TableName("t_consultation_request")
public class ConsultationRequest extends BaseEntity {

    private Long userId;
    private String phone;
    private String consultType;
    private String remark;
    private String status;
}
