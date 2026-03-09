package com.youhua.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class LoginRequest {

    @NotBlank
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "手机号格式不正确")
    private String phone;

    @NotBlank
    @Size(min = 6, max = 6, message = "验证码长度必须为6位")
    private String smsCode;

    private String deviceFingerprint;

    /** 用户同意的隐私协议版本，首次登录必填（AG-13）
     *  不在 DTO 层做 @NotBlank 校验，由 Service 层抛专用 CONSENT_REQUIRED 错误码 */
    private String consentVersion;
}
