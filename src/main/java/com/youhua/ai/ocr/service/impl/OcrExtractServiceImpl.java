package com.youhua.ai.ocr.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.enums.OcrFileType;
import com.youhua.ai.exception.AiParseException;
import com.youhua.ai.exception.AiTimeoutException;
import com.youhua.ai.exception.OcrEmptyResultException;
import com.youhua.ai.ocr.dto.OcrExtractResult;
import com.youhua.ai.ocr.dto.OcrExtractedFields;
import com.youhua.ai.ocr.dto.OcrField;
import com.youhua.ai.ocr.service.CloudOcrService;
import com.youhua.ai.ocr.service.OcrExtractService;
import com.youhua.ai.service.AiChatCaller;
import com.youhua.common.exception.BizException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Implementation of {@link OcrExtractService} using {@link AiChatCaller} (DeepSeek).
 *
 * <p>Extraction flow:
 * <ol>
 *   <li>Build system + user message prompts</li>
 *   <li>Call DeepSeek via AiChatCaller (retry + circuit breaker handled by @Resilient)</li>
 *   <li>Three-step JSON parse fallback</li>
 *   <li>Field validation (confidence filter, principal validation)</li>
 *   <li>Derived field calculation (loanDays, totalRepayment)</li>
 *   <li>Overall confidence calculation (weighted average * 100)</li>
 *   <li>Low confidence flag (overallConfidence < 60)</li>
 * </ol>
 *
 * <p>On OCR-level failures: returns {@code success=false} with errorCode.
 * Only throws {@link BizException} for AI service unavailability.
 *
 * <p><b>F-04 compliance</b>: imageBase64 is NEVER logged.
 */
@Slf4j
@Service
public class OcrExtractServiceImpl implements OcrExtractService {

    // Fields with confidence below this threshold are nulled out
    private static final BigDecimal MIN_CONFIDENCE = new BigDecimal("0.30");

    // Overall confidence threshold for lowConfidence flag (in 0-100 scale)
    private static final BigDecimal LOW_CONFIDENCE_THRESHOLD = new BigDecimal("60");

    // Core field weights (must sum to 1.0)
    private static final BigDecimal WEIGHT_CREDITOR = new BigDecimal("0.2");
    private static final BigDecimal WEIGHT_PRINCIPAL = new BigDecimal("0.4");
    private static final BigDecimal WEIGHT_LOAN_DAYS = new BigDecimal("0.2");
    private static final BigDecimal WEIGHT_TOTAL_REPAYMENT = new BigDecimal("0.2");

    private static final String SYSTEM_PROMPT = """
            你是一个专业的金融文档信息提取助手。你的任务是从用户上传的借款合同、账单或短信截图中提取债务相关的结构化信息。

            ## 严格约束
            1. 只提取以下预定义字段，不得添加额外字段
            2. 对于无法识别的字段，设置 value 为 null，confidence 为 0
            3. 对于不确定的字段，如实标注 confidence（0-1之间的小数）
            4. 不得推测或编造任何数值
            5. 金额单位统一为人民币元
            6. 利率统一转为小数形式（如4.5%写为0.045）
            7. 日期统一为 YYYY-MM-DD 格式

            ## 输出格式
            你必须且只能输出以下 JSON 结构，不要输出任何其他文字：

            {
              "creditor": { "value": "string|null", "confidence": 0.0 },
              "principal": { "value": number|null, "confidence": 0.0 },
              "totalRepayment": { "value": number|null, "confidence": 0.0 },
              "nominalRate": { "value": number|null, "confidence": 0.0 },
              "loanDays": { "value": integer|null, "confidence": 0.0 },
              "startDate": { "value": "YYYY-MM-DD|null", "confidence": 0.0 },
              "endDate": { "value": "YYYY-MM-DD|null", "confidence": 0.0 },
              "monthlyPayment": { "value": number|null, "confidence": 0.0 },
              "totalPeriods": { "value": integer|null, "confidence": 0.0 },
              "fees": { "value": number|null, "confidence": 0.0 },
              "penaltyRate": { "value": number|null, "confidence": 0.0 }
            }
            """;

    private final AiChatCaller aiChatCaller;
    private final ObjectMapper objectMapper;
    private final CloudOcrService cloudOcrService;

    public OcrExtractServiceImpl(AiChatCaller aiChatCaller, ObjectMapper objectMapper,
                                 CloudOcrService cloudOcrService) {
        this.aiChatCaller = aiChatCaller;
        this.objectMapper = objectMapper;
        this.cloudOcrService = cloudOcrService;
    }

    @Override
    public OcrExtractResult extract(String imageBase64, OcrFileType fileType) {
        // F-04: NEVER log imageBase64 content
        log.info("[OcrExtractService] Starting extraction fileType={}", fileType);
        long startTime = System.currentTimeMillis();

        // Step 1: Use cloud OCR to extract raw text from image
        String ocrText;
        try {
            ocrText = cloudOcrService.extractText(imageBase64);
        } catch (BizException e) {
            log.error("[OcrExtractService] Cloud OCR failed", e);
            return OcrExtractResult.builder()
                    .success(false)
                    .errorCode("OCR_FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }

        log.debug("[OcrExtractService] Cloud OCR text length={}", ocrText.length());

        // Step 2: Send extracted text (not base64) to DeepSeek for structured extraction
        // Retry + circuit breaker is handled by @Resilient on AiChatCaller.callForOcr
        String userPrompt = buildUserPrompt(ocrText, fileType);
        String rawJson;

        try {
            rawJson = aiChatCaller.callForOcr(SYSTEM_PROMPT, userPrompt);
            // Debug log truncated to 2000 chars per spec 6.1.8
            log.debug("[OcrExtractService] AI response rawJson={}", rawJson != null && rawJson.length() > 2000
                    ? rawJson.substring(0, 2000) + "..." : rawJson);
        } catch (AiParseException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[OcrExtractService] All parse attempts failed elapsed={}ms", elapsed);
            return OcrExtractResult.builder()
                    .success(false)
                    .errorCode("OCR_PARSE_ERROR")
                    .errorMessage("AI 返回内容无法解析，请重试或手动录入")
                    .build();
        } catch (AiTimeoutException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[OcrExtractService] AI call timed out after all retries elapsed={}ms", elapsed);
            return OcrExtractResult.builder()
                    .success(false)
                    .errorCode("OCR_TIMEOUT")
                    .errorMessage("AI 识别超时，请重试")
                    .build();
        } catch (BizException e) {
            // AI_UNAVAILABLE — propagate upward
            throw e;
        }

        try {
            OcrExtractResult result = parseAndPostProcess(rawJson, fileType);
            long elapsed = System.currentTimeMillis() - startTime;
            log.info("[OcrExtractService] Extraction success overallConfidence={} lowConfidence={} elapsed={}ms",
                    result.getOverallConfidence(), result.isLowConfidence(), elapsed);
            return result;
        } catch (AiParseException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[OcrExtractService] Parse failed after AI call elapsed={}ms", elapsed);
            return OcrExtractResult.builder()
                    .success(false)
                    .errorCode("OCR_PARSE_ERROR")
                    .errorMessage("AI 返回内容无法解析，请重试或手动录入")
                    .build();
        } catch (OcrEmptyResultException e) {
            long elapsed = System.currentTimeMillis() - startTime;
            log.error("[OcrExtractService] OCR result empty elapsed={}ms", elapsed);
            return OcrExtractResult.builder()
                    .success(false)
                    .errorCode("OCR_FAILED")
                    .errorMessage(e.getMessage())
                    .build();
        }
    }

    // ===== Private: Prompt building =====

    private String buildUserPrompt(String ocrText, OcrFileType fileType) {
        String fileTypeLabel = switch (fileType) {
            case CONTRACT -> "CONTRACT";
            case BILL -> "BILL";
            case SMS_SCREENSHOT -> "SMS_SCREENSHOT";
        };

        String supplementary = switch (fileType) {
            case CONTRACT -> """
                    重点关注：合同首页的借款金额和利率条款、借款期限、费用明细（服务费、手续费、保险费等）、提前还款违约金条款。
                    计算提示：等额本息时 totalRepayment = monthlyPayment x totalPeriods；先息后本时 totalRepayment = principal + (principal x nominalRate x loanDays/365) + fees。
                    """;
            case BILL -> """
                    重点关注：账单周期和应还金额、分期手续费/利息明细、逾期费用（如有）。
                    注意：信用卡账单的"应还金额"是本期应还，不是总还款额；单期账单 confidence 设低（<0.6）。
                    """;
            case SMS_SCREENSHOT -> """
                    重点关注：放款通知中的金额和期限、还款提醒中的应还金额和日期、逾期通知中的逾期金额和罚息。
                    注意：短信信息通常不完整，confidence 应整体偏低；缺失字段直接设置 value 为 null。
                    """;
        };

        return String.format("""
                请从以下OCR识别文本中提取债务信息。

                文件类型：%s
                  - CONTRACT: 借款合同
                  - BILL: 账单/还款计划
                  - SMS_SCREENSHOT: 短信截图/APP截图

                注意事项：
                - 如果文本中包含多笔借款信息，只提取主要的一笔（金额最大的）
                - 如果利率标注为"年化"则直接使用，如果标注为"月利率"需要x12转换
                - 如果没有明确的总还款额，但有期数和月供，请计算：totalRepayment = monthlyPayment x totalPeriods
                - fees 包括手续费、服务费、管理费等所有非利息费用的总和

                %s

                [OCR识别文本]
                %s
                """,
                fileTypeLabel, supplementary, ocrText);
    }

    // ===== Private: Parse & post-process =====

    /**
     * Three-step JSON parse fallback + post-processing.
     * Package-visible for unit testing.
     */
    OcrExtractResult parseAndPostProcess(String rawJson, OcrFileType fileType) {
        if (rawJson == null || rawJson.isBlank()) {
            throw new OcrEmptyResultException("AI 返回空结果，图片可能不清晰，建议手动录入");
        }

        JsonNode root = tryParseJson(rawJson);
        if (root == null) {
            throw new AiParseException("AI 返回内容无法解析为 JSON");
        }

        log.debug("[OcrExtractService] JSON parsed successfully, starting field extraction");

        // ===== Step 2: Field extraction with confidence filtering =====

        OcrField<String> creditor = extractStringField(root, "creditor");
        OcrField<BigDecimal> principal = extractDecimalField(root, "principal");
        OcrField<BigDecimal> totalRepayment = extractDecimalField(root, "totalRepayment");
        OcrField<BigDecimal> nominalRate = extractDecimalField(root, "nominalRate");
        OcrField<Integer> loanDays = extractIntegerField(root, "loanDays");
        OcrField<LocalDate> startDate = extractDateField(root, "startDate");
        OcrField<LocalDate> endDate = extractDateField(root, "endDate");
        OcrField<BigDecimal> monthlyPayment = extractDecimalField(root, "monthlyPayment");
        OcrField<Integer> totalPeriods = extractIntegerField(root, "totalPeriods");
        OcrField<BigDecimal> fees = extractDecimalField(root, "fees");
        OcrField<BigDecimal> penaltyRate = extractDecimalField(root, "penaltyRate");

        // Capture raw AI-reported confidences before any derivation.
        BigDecimal rawLoanDaysConf = loanDays.getConfidence();
        BigDecimal rawTotalRepaymentConf = totalRepayment.getConfidence();

        // Validate principal > 0
        if (principal.getValue() != null && principal.getValue().compareTo(BigDecimal.ZERO) <= 0) {
            log.debug("[OcrExtractService] principal <= 0, nulling out");
            principal = new OcrField<>(null, BigDecimal.ZERO);
        }

        // Derive loanDays from startDate + endDate
        if (loanDays.getValue() == null && startDate.getValue() != null && endDate.getValue() != null) {
            int derived = (int) ChronoUnit.DAYS.between(startDate.getValue(), endDate.getValue());
            BigDecimal derivedConf = startDate.getConfidence().min(endDate.getConfidence());
            loanDays = new OcrField<>(derived, derivedConf);
            if (rawLoanDaysConf.compareTo(BigDecimal.ZERO) > 0) {
                rawLoanDaysConf = derivedConf;
            }
            log.debug("[OcrExtractService] derived loanDays={} from startDate+endDate conf={}", derived, derivedConf);
        }

        // Derive totalRepayment from monthlyPayment * totalPeriods
        if (totalRepayment.getValue() == null
                && monthlyPayment.getValue() != null
                && totalPeriods.getValue() != null) {
            BigDecimal derived = monthlyPayment.getValue().multiply(new BigDecimal(totalPeriods.getValue()));
            BigDecimal derivedConf = monthlyPayment.getConfidence().min(totalPeriods.getConfidence());
            totalRepayment = new OcrField<>(derived, derivedConf);
            if (rawTotalRepaymentConf.compareTo(BigDecimal.ZERO) > 0) {
                rawTotalRepaymentConf = derivedConf;
            }
            log.debug("[OcrExtractService] derived totalRepayment={} from monthlyPayment*totalPeriods conf={}", derived, derivedConf);
        }

        // ===== Step 3: Overall confidence (weighted, then *100) =====
        BigDecimal creditorConf = creditor.getConfidence();
        BigDecimal principalConf = principal.getConfidence();
        BigDecimal loanDaysConf = rawLoanDaysConf;
        BigDecimal totalRepaymentConf = rawTotalRepaymentConf;

        BigDecimal overallConfidence = creditorConf.multiply(WEIGHT_CREDITOR)
                .add(principalConf.multiply(WEIGHT_PRINCIPAL))
                .add(loanDaysConf.multiply(WEIGHT_LOAN_DAYS))
                .add(totalRepaymentConf.multiply(WEIGHT_TOTAL_REPAYMENT))
                .multiply(new BigDecimal("100"))
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("[OcrExtractService] overallConfidence={} (creditor={} principal={} loanDays={} totalRepayment={})",
                overallConfidence, creditorConf, principalConf, loanDaysConf, totalRepaymentConf);

        // ===== Step 4: Check all core fields null → failure =====
        boolean hasMeaningfulData = principal.getValue() != null
                || creditor.getValue() != null
                || totalRepayment.getValue() != null
                || loanDays.getValue() != null;

        if (!hasMeaningfulData) {
            throw new OcrEmptyResultException("图片内容无法识别，建议手动录入");
        }

        boolean lowConf = overallConfidence.compareTo(LOW_CONFIDENCE_THRESHOLD) < 0;
        if (lowConf) {
            log.warn("[OcrExtractService] Low confidence result overallConfidence={}", overallConfidence);
        }

        OcrExtractedFields fields = OcrExtractedFields.builder()
                .creditor(creditor)
                .principal(principal)
                .totalRepayment(totalRepayment)
                .nominalRate(nominalRate)
                .loanDays(loanDays)
                .startDate(startDate)
                .endDate(endDate)
                .monthlyPayment(monthlyPayment)
                .totalPeriods(totalPeriods)
                .fees(fees)
                .penaltyRate(penaltyRate)
                .build();

        return OcrExtractResult.builder()
                .success(true)
                .overallConfidence(overallConfidence)
                .lowConfidence(lowConf)
                .fields(fields)
                .build();
    }

    // ===== Private: JSON parsing (three-step fallback) =====

    private JsonNode tryParseJson(String raw) {
        // Step 1: Direct parse
        JsonNode node = tryParse(raw);
        if (node != null) return node;

        // Step 2: Extract from markdown code block
        String stripped = stripMarkdownCodeBlock(raw);
        if (stripped != null) {
            node = tryParse(stripped);
            if (node != null) return node;
        }

        // Step 3: Extract first { ... } span
        int firstBrace = raw.indexOf('{');
        int lastBrace = raw.lastIndexOf('}');
        if (firstBrace >= 0 && lastBrace > firstBrace) {
            node = tryParse(raw.substring(firstBrace, lastBrace + 1));
            if (node != null) return node;
        }

        return null;
    }

    private JsonNode tryParse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private String stripMarkdownCodeBlock(String raw) {
        String trimmed = raw.trim();
        if (!trimmed.startsWith("```")) return null;
        int newline = trimmed.indexOf('\n');
        if (newline < 0) return null;
        int closing = trimmed.lastIndexOf("```");
        if (closing <= newline) return null;
        return trimmed.substring(newline + 1, closing).trim();
    }

    // ===== Private: Field extractors =====

    private OcrField<String> extractStringField(JsonNode root, String name) {
        BigDecimal conf = readConfidence(root, name);
        if (conf.compareTo(MIN_CONFIDENCE) < 0) {
            return new OcrField<>(null, BigDecimal.ZERO);
        }
        JsonNode valueNode = readValueNode(root, name);
        if (valueNode == null || valueNode.isNull()) return new OcrField<>(null, conf);
        return new OcrField<>(valueNode.asText(null), conf);
    }

    private OcrField<BigDecimal> extractDecimalField(JsonNode root, String name) {
        BigDecimal conf = readConfidence(root, name);
        boolean aiProvidedConf = conf.compareTo(BigDecimal.ZERO) > 0;
        if (conf.compareTo(MIN_CONFIDENCE) < 0) {
            return new OcrField<>(null, aiProvidedConf ? conf : BigDecimal.ZERO);
        }
        JsonNode valueNode = readValueNode(root, name);
        if (valueNode == null || valueNode.isNull()) return new OcrField<>(null, BigDecimal.ZERO);
        try {
            BigDecimal val = new BigDecimal(valueNode.asText());
            return new OcrField<>(val, conf);
        } catch (NumberFormatException e) {
            log.warn("[OcrExtractService] Failed to parse decimal field={} value={}", name, valueNode.asText());
            return new OcrField<>(null, BigDecimal.ZERO);
        }
    }

    private OcrField<Integer> extractIntegerField(JsonNode root, String name) {
        BigDecimal conf = readConfidence(root, name);
        if (conf.compareTo(MIN_CONFIDENCE) < 0) {
            return new OcrField<>(null, BigDecimal.ZERO);
        }
        JsonNode valueNode = readValueNode(root, name);
        if (valueNode == null || valueNode.isNull()) return new OcrField<>(null, BigDecimal.ZERO);
        return new OcrField<>(valueNode.asInt(), conf);
    }

    private OcrField<LocalDate> extractDateField(JsonNode root, String name) {
        BigDecimal conf = readConfidence(root, name);
        if (conf.compareTo(MIN_CONFIDENCE) < 0) {
            return new OcrField<>(null, BigDecimal.ZERO);
        }
        JsonNode valueNode = readValueNode(root, name);
        if (valueNode == null || valueNode.isNull()) return new OcrField<>(null, BigDecimal.ZERO);
        try {
            LocalDate date = LocalDate.parse(valueNode.asText());
            return new OcrField<>(date, conf);
        } catch (Exception e) {
            log.warn("[OcrExtractService] Failed to parse date field={} value={}", name, valueNode.asText());
            return new OcrField<>(null, BigDecimal.ZERO);
        }
    }

    private BigDecimal readConfidence(JsonNode root, String name) {
        JsonNode fieldNode = root.get(name);
        if (fieldNode == null || fieldNode.isNull()) return BigDecimal.ZERO;
        JsonNode confNode = fieldNode.get("confidence");
        if (confNode == null || confNode.isNull()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(confNode.asText()).setScale(2, RoundingMode.HALF_UP);
        } catch (NumberFormatException e) {
            return BigDecimal.ZERO;
        }
    }

    private JsonNode readValueNode(JsonNode root, String name) {
        JsonNode fieldNode = root.get(name);
        if (fieldNode == null || fieldNode.isNull()) return null;
        return fieldNode.get("value");
    }
}
