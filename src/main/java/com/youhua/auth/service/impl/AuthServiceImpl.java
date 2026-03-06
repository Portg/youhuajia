package com.youhua.auth.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.youhua.auth.dto.request.LoginRequest;
import com.youhua.auth.dto.request.SendSmsRequest;
import com.youhua.auth.dto.response.LoginResponse;
import com.youhua.auth.entity.User;
import com.youhua.auth.enums.UserStatus;
import com.youhua.auth.mapper.UserMapper;
import com.youhua.auth.service.AuthService;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.security.SecureRandom;
import java.util.concurrent.TimeUnit;

/**
 * Auth service implementation: SMS verification code + JWT session management.
 *
 * <p>F-04 compliance: Phone numbers are NEVER logged in plain text.
 * All phone references in logs use maskPhone().
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private static final String SMS_CODE_KEY_PREFIX = "sms:code:";
    private static final String SMS_LIMIT_KEY_PREFIX = "sms:limit:";
    private static final String SESSION_KEY_PREFIX = "session:";
    private static final long SMS_CODE_TTL_MINUTES = 5;
    private static final long SMS_LIMIT_TTL_SECONDS = 60;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserMapper userMapper;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${youhua.auth.jwt-secret:youhuajia-dev-secret-key-2024}")
    private String jwtSecret;

    /** JWT expiration in seconds, defaults to 30 days (2592000) */
    @Value("${youhua.auth.jwt-expiration:2592000}")
    private long jwtExpirationSeconds;

    @Override
    public void sendSms(SendSmsRequest request) {
        String phone = request.getPhone();
        String limitKey = SMS_LIMIT_KEY_PREFIX + phone;

        // Frequency limit check
        if (Boolean.TRUE.equals(redisTemplate.hasKey(limitKey))) {
            log.warn("[AuthService] SMS rate limit hit phone={}", maskPhone(phone));
            throw new BizException(ErrorCode.SMS_RATE_LIMIT);
        }

        // Generate 6-digit code
        String code = String.format("%06d", SECURE_RANDOM.nextInt(1000000));

        // MVP: mock SMS — F-04 phone must be masked in log
        log.info("[AuthService] SMS_MOCK: phone={}, code={}", maskPhone(phone), code);

        // Store code and rate limit flag
        String smsKey = SMS_CODE_KEY_PREFIX + phone;
        redisTemplate.opsForValue().set(smsKey, code, SMS_CODE_TTL_MINUTES, TimeUnit.MINUTES);
        redisTemplate.opsForValue().set(limitKey, "1", SMS_LIMIT_TTL_SECONDS, TimeUnit.SECONDS);
    }

    @Override
    public LoginResponse createSession(LoginRequest request) {
        String phone = request.getPhone();
        String smsKey = SMS_CODE_KEY_PREFIX + phone;

        // Retrieve and validate code
        String storedCode = redisTemplate.opsForValue().get(smsKey);
        if (storedCode == null || !storedCode.equals(request.getSmsCode())) {
            log.warn("[AuthService] SMS code invalid phone={}", maskPhone(phone));
            throw new BizException(ErrorCode.SMS_CODE_INVALID);
        }

        // One-time use: delete code after validation
        redisTemplate.delete(smsKey);

        // Look up user by phoneHash
        String phoneHash = hashPhone(phone);
        User user = userMapper.selectOne(
                new LambdaQueryWrapper<User>().eq(User::getPhoneHash, phoneHash));

        boolean isNewUser = (user == null);
        if (isNewUser) {
            // Auto-register
            user = new User();
            user.setPhone(phone);
            user.setPhoneHash(phoneHash);
            user.setNickname("用户" + phone.substring(phone.length() - 4));
            user.setStatus(UserStatus.ACTIVE);
            userMapper.insert(user);
            log.info("[AuthService] Auto-registered new user userId={} phone={}", user.getId(), maskPhone(phone));
        } else {
            // Check account status
            if (UserStatus.FROZEN.equals(user.getStatus())) {
                throw new BizException(ErrorCode.ACCOUNT_FROZEN);
            }
            if (UserStatus.CANCELLED.equals(user.getStatus())) {
                throw new BizException(ErrorCode.ACCOUNT_CANCELLED);
            }
        }

        // Update last login info
        user.setLastLoginTime(LocalDateTime.now());
        userMapper.updateById(user);

        // Generate JWT
        String token = generateJwt(user.getId(), phone);

        // Store session in Redis
        String sessionKey = SESSION_KEY_PREFIX + user.getId();
        redisTemplate.opsForValue().set(sessionKey, token, jwtExpirationSeconds, TimeUnit.SECONDS);

        log.info("[AuthService] Session created userId={} newUser={}", user.getId(), isNewUser);

        return LoginResponse.builder()
                .accessToken(token)
                .refreshToken(token)
                .expiresIn((int) jwtExpirationSeconds)
                .userId("users/" + user.getId())
                .newUser(isNewUser)
                .build();
    }

    @Override
    public LoginResponse refreshSession(String refreshToken) {
        // Parse userId directly from the refresh token — /auth/ paths are skipped by JwtAuthFilter
        // so RequestContext is not populated on this endpoint.
        Long userId = verifyJwtAndGetUserId(refreshToken);

        String sessionKey = SESSION_KEY_PREFIX + userId;
        String storedToken = redisTemplate.opsForValue().get(sessionKey);
        if (storedToken == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "Session expired, please login again");
        }

        com.youhua.auth.entity.User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "User not found");
        }

        String newToken = generateJwt(userId, user.getPhone());
        redisTemplate.opsForValue().set(sessionKey, newToken, jwtExpirationSeconds, TimeUnit.SECONDS);

        log.info("[AuthService] Session refreshed userId={}", userId);

        return LoginResponse.builder()
                .accessToken(newToken)
                .refreshToken(newToken)
                .expiresIn((int) jwtExpirationSeconds)
                .userId("users/" + userId)
                .newUser(false)
                .build();
    }

    @Override
    public void revokeSession() {
        // TODO MVP 暂不实现
        log.info("[AuthService] revokeSession called (MVP: no-op)");
    }

    /**
     * Mask phone number for logging compliance (F-04).
     * Example: 13812341234 -> 138****1234
     */
    public String maskPhone(String phone) {
        if (phone == null || phone.length() < 7) {
            return "****";
        }
        return phone.substring(0, 3) + "****" + phone.substring(phone.length() - 4);
    }

    /**
     * Hash phone number for database storage (SHA-256).
     */
    public String hashPhone(String phone) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(phone.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "Phone hash failed", e);
        }
    }

    /**
     * Generate JWT token with HMAC-SHA256.
     * Payload: {userId, exp}
     */
    public String generateJwt(Long userId, String phone) {
        try {
            String header = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString("{\"alg\":\"HS256\",\"typ\":\"JWT\"}".getBytes(StandardCharsets.UTF_8));

            long exp = System.currentTimeMillis() / 1000 + jwtExpirationSeconds;
            String payloadJson = String.format("{\"userId\":%d,\"exp\":%d}", userId, exp);
            String payload = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

            String signingInput = header + "." + payload;
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] sigBytes = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String signature = Base64.getUrlEncoder().withoutPadding().encodeToString(sigBytes);

            return signingInput + "." + signature;
        } catch (Exception e) {
            throw new BizException(ErrorCode.SYSTEM_BUSY, "JWT generation failed", e);
        }
    }

    /**
     * Verify and parse JWT token. Returns userId extracted from payload.
     */
    public Long verifyJwtAndGetUserId(String token) {
        try {
            String[] parts = token.split("\\.");
            if (parts.length != 3) {
                throw new BizException(ErrorCode.TOKEN_INVALID);
            }

            String signingInput = parts[0] + "." + parts[1];
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expectedSigBytes = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
            String expectedSig = Base64.getUrlEncoder().withoutPadding().encodeToString(expectedSigBytes);

            if (!expectedSig.equals(parts[2])) {
                throw new BizException(ErrorCode.TOKEN_INVALID);
            }

            String payloadJson = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);

            JsonNode payloadNode = objectMapper.readTree(payloadJson);
            long userId = payloadNode.get("userId").asLong();
            long exp = payloadNode.get("exp").asLong();

            if (System.currentTimeMillis() / 1000 > exp) {
                throw new BizException(ErrorCode.TOKEN_EXPIRED);
            }

            return userId;
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException(ErrorCode.TOKEN_INVALID, "Token parsing failed", e);
        }
    }
}
