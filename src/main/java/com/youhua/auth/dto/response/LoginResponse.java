package com.youhua.auth.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class LoginResponse {

    private String accessToken;
    private String refreshToken;
    /** Token 有效期（秒） */
    private Integer expiresIn;
    /** 资源名称格式: users/{userId} */
    private String userId;
    /** 是否新注册用户（AIP-140：布尔字段不加 is 前缀） */
    private Boolean newUser;
}
