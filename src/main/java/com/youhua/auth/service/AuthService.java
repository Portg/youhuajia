package com.youhua.auth.service;

import com.youhua.auth.dto.request.LoginRequest;
import com.youhua.auth.dto.request.SendSmsRequest;
import com.youhua.auth.dto.response.LoginResponse;

public interface AuthService {

    void sendSms(SendSmsRequest request);

    LoginResponse createSession(LoginRequest request);

    LoginResponse refreshSession(String refreshToken);

    void revokeSession();

    /**
     * Verify a JWT token and extract the userId.
     * Used by JwtAuthFilter for request authentication.
     */
    Long verifyJwtAndGetUserId(String token);
}
