package com.youhua.profile.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateConsultationRequest {

    @NotBlank(message = "联系手机号不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank(message = "咨询类型不能为空")
    @Pattern(regexp = "^(DEBT_OPTIMIZATION|RATE_NEGOTIATION|GENERAL)$", message = "无效的咨询类型")
    private String consultType;

    @Size(max = 500, message = "补充说明不超过500字")
    private String remark;
}
