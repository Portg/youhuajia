package com.youhua.auth.service;

import com.youhua.auth.dto.request.LoginRequest;
import com.youhua.auth.dto.request.SendSmsRequest;
import com.youhua.auth.dto.response.LoginResponse;
import com.youhua.auth.entity.User;
import com.youhua.auth.enums.UserStatus;
import com.youhua.auth.mapper.UserMapper;
import com.youhua.auth.service.impl.AuthServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceImplTest {

    @Mock
    private UserMapper userMapper;

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private AuthServiceImpl authService;

    private static final String TEST_PHONE = "13812341234";

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authService, "jwtSecret", "youhuajia-test-secret-key-2024");
        ReflectionTestUtils.setField(authService, "jwtExpirationSeconds", 604800L);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void should_throw_SMS_RATE_LIMIT_when_rate_limit_key_exists() {
        // Given
        when(redisTemplate.hasKey("sms:limit:" + TEST_PHONE)).thenReturn(true);

        SendSmsRequest request = new SendSmsRequest();
        request.setPhone(TEST_PHONE);

        // When & Then
        assertThatThrownBy(() -> authService.sendSms(request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SMS_RATE_LIMIT));

        verify(valueOperations, never()).set(anyString(), anyString(), anyLong(), any(TimeUnit.class));
    }

    @Test
    void should_send_sms_and_store_code_when_no_rate_limit() {
        // Given
        when(redisTemplate.hasKey("sms:limit:" + TEST_PHONE)).thenReturn(false);

        SendSmsRequest request = new SendSmsRequest();
        request.setPhone(TEST_PHONE);

        // When
        authService.sendSms(request);

        // Then
        verify(valueOperations).set(eq("sms:code:" + TEST_PHONE), anyString(), eq(5L), eq(TimeUnit.MINUTES));
        verify(valueOperations).set(eq("sms:limit:" + TEST_PHONE), eq("1"), eq(60L), eq(TimeUnit.SECONDS));
    }

    @Test
    void should_throw_SMS_CODE_INVALID_when_code_is_wrong() {
        // Given
        when(valueOperations.get("sms:code:" + TEST_PHONE)).thenReturn("123456");

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setSmsCode("999999");

        // When & Then
        assertThatThrownBy(() -> authService.createSession(request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SMS_CODE_INVALID));
    }

    @Test
    void should_throw_SMS_CODE_INVALID_when_code_is_expired() {
        // Given
        when(valueOperations.get("sms:code:" + TEST_PHONE)).thenReturn(null);

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setSmsCode("123456");

        // When & Then
        assertThatThrownBy(() -> authService.createSession(request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.SMS_CODE_INVALID));
    }

    @Test
    void should_auto_register_and_return_token_when_user_not_exists() {
        // Given
        when(valueOperations.get("sms:code:" + TEST_PHONE)).thenReturn("123456");
        when(userMapper.selectOne(any())).thenReturn(null);
        doAnswer(inv -> {
            User u = inv.getArgument(0);
            u.setId(1001L);
            return 1;
        }).when(userMapper).insert((User) any(User.class));

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setSmsCode("123456");

        // When
        LoginResponse response = authService.createSession(request);

        // Then
        assertThat(response.getNewUser()).isTrue();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getExpiresIn()).isEqualTo((int) (7 * 24 * 3600));

        // Verify user inserted
        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert((User) userCaptor.capture());
        User insertedUser = userCaptor.getValue();
        assertThat(insertedUser.getNickname()).contains("1234");
        assertThat(insertedUser.getStatus()).isEqualTo(UserStatus.ACTIVE);
    }

    @Test
    void should_login_existing_user_and_return_token_when_code_correct() {
        // Given
        User existingUser = new User();
        existingUser.setId(2001L);
        existingUser.setPhone(TEST_PHONE);
        existingUser.setStatus(UserStatus.ACTIVE);
        existingUser.setNickname("用户1234");

        when(valueOperations.get("sms:code:" + TEST_PHONE)).thenReturn("654321");
        when(userMapper.selectOne(any())).thenReturn(existingUser);

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setSmsCode("654321");

        // When
        LoginResponse response = authService.createSession(request);

        // Then
        assertThat(response.getNewUser()).isFalse();
        assertThat(response.getAccessToken()).isNotBlank();
        assertThat(response.getUserId()).isEqualTo("users/2001");
        verify(userMapper, never()).insert((User) any(User.class));
    }

    @Test
    void should_throw_ACCOUNT_FROZEN_when_user_is_frozen() {
        // Given
        User frozenUser = new User();
        frozenUser.setId(3001L);
        frozenUser.setStatus(UserStatus.FROZEN);

        when(valueOperations.get("sms:code:" + TEST_PHONE)).thenReturn("112233");
        when(userMapper.selectOne(any())).thenReturn(frozenUser);

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setSmsCode("112233");

        // When & Then
        assertThatThrownBy(() -> authService.createSession(request))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> assertThat(((BizException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.ACCOUNT_FROZEN));
    }

    @Test
    void should_delete_sms_code_after_successful_login() {
        // Given
        User user = new User();
        user.setId(4001L);
        user.setStatus(UserStatus.ACTIVE);

        when(valueOperations.get("sms:code:" + TEST_PHONE)).thenReturn("445566");
        when(userMapper.selectOne(any())).thenReturn(user);

        LoginRequest request = new LoginRequest();
        request.setPhone(TEST_PHONE);
        request.setSmsCode("445566");

        // When
        authService.createSession(request);

        // Then: code should be deleted (one-time use)
        verify(redisTemplate).delete("sms:code:" + TEST_PHONE);
    }

    @Test
    void should_mask_phone_correctly() {
        // When & Then - maskPhone is public
        assertThat(authService.maskPhone("13812341234")).isEqualTo("138****1234");
        assertThat(authService.maskPhone("13600000001")).isEqualTo("136****0001");
        assertThat(authService.maskPhone(null)).isEqualTo("****");
        assertThat(authService.maskPhone("123")).isEqualTo("****");
    }

    @Test
    void should_generate_valid_jwt_and_verify_userId() {
        // When
        String token = authService.generateJwt(9001L, TEST_PHONE);

        // Then
        assertThat(token).isNotBlank();
        assertThat(token.split("\\.")).hasSize(3);

        Long userId = authService.verifyJwtAndGetUserId(token);
        assertThat(userId).isEqualTo(9001L);
    }
}
