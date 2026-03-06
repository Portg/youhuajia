package com.youhua.ai.ocr.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.ocr.service.CloudOcrService;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.infra.resilience.Resilient;
import com.youhua.infra.resilience.TimeoutSpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.StringJoiner;

/**
 * Baidu Cloud OCR implementation.
 *
 * <p>Uses Baidu's General OCR (accurate version) API to extract text from images.
 * Access token is obtained via API Key + Secret Key and cached until expiry.
 *
 * <p>F-04: imageBase64 content is NEVER logged.
 */
@Slf4j
@Service
public class BaiduOcrServiceImpl implements CloudOcrService {

    private static final String TOKEN_URL = "https://aip.baidubce.com/oauth/2.0/token";
    private static final String OCR_URL = "https://aip.baidubce.com/rest/2.0/ocr/v1/accurate_basic";

    @Value("${youhua.ai.baidu-ocr.api-key:}")
    private String apiKey;

    @Value("${youhua.ai.baidu-ocr.secret-key:}")
    private String secretKey;

    private final RestClient restClient;
    private final ObjectMapper objectMapper;

    private volatile String cachedAccessToken;
    private volatile long tokenExpiryTime;

    public BaiduOcrServiceImpl(ObjectMapper objectMapper) {
        this.restClient = RestClient.create();
        this.objectMapper = objectMapper;
    }

    @Override
    @Resilient(circuitBreaker = "baiduOcr", timeout = @TimeoutSpec(seconds = 15))
    public String extractText(String imageBase64) {
        log.info("[BaiduOcr] Starting text extraction");
        long startTime = System.currentTimeMillis();

        String accessToken = getAccessToken();

        String body = "image=" + URLEncoder.encode(imageBase64, StandardCharsets.UTF_8);

        String response;
        try {
            response = restClient.post()
                    .uri(OCR_URL + "?access_token=" + accessToken)
                    .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_FORM_URLENCODED_VALUE)
                    .body(body)
                    .retrieve()
                    .body(String.class);
        } catch (Exception e) {
            log.error("[BaiduOcr] API call failed", e);
            throw new BizException(ErrorCode.AI_UNAVAILABLE, "百度 OCR 服务调用失败: " + e.getMessage(), e);
        }

        String extractedText = parseOcrResponse(response);
        long elapsed = System.currentTimeMillis() - startTime;
        log.info("[BaiduOcr] Text extraction completed chars={} elapsed={}ms", extractedText.length(), elapsed);

        return extractedText;
    }

    private String getAccessToken() {
        if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
            return cachedAccessToken;
        }

        if (apiKey == null || apiKey.isBlank() || secretKey == null || secretKey.isBlank()) {
            throw new BizException(ErrorCode.AI_UNAVAILABLE, "百度 OCR API Key 未配置");
        }

        synchronized (this) {
            if (cachedAccessToken != null && System.currentTimeMillis() < tokenExpiryTime) {
                return cachedAccessToken;
            }

            String url = TOKEN_URL + "?grant_type=client_credentials&client_id=" + apiKey + "&client_secret=" + secretKey;
            try {
                String response = restClient.post()
                        .uri(url)
                        .retrieve()
                        .body(String.class);

                JsonNode node = objectMapper.readTree(response);
                if (node.has("error")) {
                    String error = node.get("error").asText();
                    log.error("[BaiduOcr] Token request failed error={}", error);
                    throw new BizException(ErrorCode.AI_UNAVAILABLE, "百度 OCR 认证失败: " + error);
                }

                cachedAccessToken = node.get("access_token").asText();
                int expiresIn = node.get("expires_in").asInt();
                // Refresh 5 minutes before expiry
                tokenExpiryTime = System.currentTimeMillis() + (expiresIn - 300) * 1000L;

                log.info("[BaiduOcr] Access token refreshed expiresIn={}s", expiresIn);
                return cachedAccessToken;
            } catch (BizException e) {
                throw e;
            } catch (Exception e) {
                log.error("[BaiduOcr] Token request failed", e);
                throw new BizException(ErrorCode.AI_UNAVAILABLE, "百度 OCR 认证失败: " + e.getMessage(), e);
            }
        }
    }

    private String parseOcrResponse(String response) {
        try {
            JsonNode root = objectMapper.readTree(response);

            if (root.has("error_code")) {
                int errorCode = root.get("error_code").asInt();
                String errorMsg = root.has("error_msg") ? root.get("error_msg").asText() : "未知错误";
                log.error("[BaiduOcr] OCR API error code={} msg={}", errorCode, errorMsg);
                throw new BizException(ErrorCode.OCR_FAILED, "百度 OCR 识别失败: " + errorMsg);
            }

            JsonNode wordsResult = root.get("words_result");
            if (wordsResult == null || !wordsResult.isArray() || wordsResult.isEmpty()) {
                log.warn("[BaiduOcr] No text recognized from image");
                throw new BizException(ErrorCode.OCR_FAILED, "图片中未识别到文字，请重新上传清晰图片");
            }

            StringJoiner joiner = new StringJoiner("\n");
            for (JsonNode item : wordsResult) {
                JsonNode words = item.get("words");
                if (words != null) {
                    joiner.add(words.asText());
                }
            }

            return joiner.toString();
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[BaiduOcr] Failed to parse OCR response", e);
            throw new BizException(ErrorCode.OCR_FAILED, "OCR 响应解析失败", e);
        }
    }
}
