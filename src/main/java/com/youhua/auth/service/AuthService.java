package com.youhua.auth.service;

import com.youhua.auth.dto.request.LoginRequest;
import com.youhua.auth.dto.request.SendSmsRequest;
import com.youhua.auth.dto.response.LoginResponse;

public interface AuthService {

    void sendSms(SendSmsRequest request);

    LoginResponse createSession(LoginRequest request);

    LoginResponse refreshSession();

    void revokeSession();
}
