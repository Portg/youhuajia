package com.youhua.auth.controller;

import com.youhua.auth.dto.request.LoginRequest;
import com.youhua.auth.dto.request.SendSmsRequest;
import com.youhua.auth.dto.response.LoginResponse;
import com.youhua.auth.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "auth", description = "认证")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "发送验证码")
    @PostMapping("/auth/sms:send")
    public void sendSms(@Valid @RequestBody SendSmsRequest request) {
        authService.sendSms(request);
    }

    @Operation(summary = "验证码登录（创建会话，自动注册）")
    @PostMapping("/auth/sessions")
    public LoginResponse createSession(@Valid @RequestBody LoginRequest request) {
        return authService.createSession(request);
    }

    @Operation(summary = "刷新 Token")
    @PostMapping("/auth/sessions:refresh")
    public LoginResponse refreshSession() {
        return authService.refreshSession();
    }

    @Operation(summary = "退出登录（撤销会话）")
    @PostMapping("/auth/sessions:revoke")
    public void revokeSession() {
        authService.revokeSession();
    }
}
