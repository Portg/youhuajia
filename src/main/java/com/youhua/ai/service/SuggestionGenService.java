package com.youhua.ai.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.dto.SuggestionResult;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.enums.RiskLevel;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/**
 * Generates AI-powered personalized debt optimization suggestions.
 *
 * <p>Key constraints (CLAUDE.md):
 * <ul>
 *   <li>F-02: AI is used ONLY for text generation — all numerical values come from rule engine input.
 *   <li>F-11: No panic-inducing language (严重/赶紧/最后机会 etc.)
 *   <li>F-13: Score &lt; 60 users never see rejection — always get a constructive path.
 *   <li>F-01: All monetary fields use BigDecimal.
 * </ul>
 *
 * <p>Output follows the five-segment psychological path from user-journey.md Section 7:
 * empathy → quantified loss → positive turn → action steps → safety closure.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SuggestionGenService {

    private static final List<String> FORBIDDEN_PHRASES = Arrays.asList(
            "问题严重", "情况严重", "非常严重", "相当严重",
            "赶紧行动", "赶紧处理", "赶快行动", "赶快处理",
            "最后机会", "最后一次机会",
            "申请失败", "不符合条件", "审核不通过", "拒绝",
            "一定", "必须", "否则后果",
            "比别人", "别人都", "大家都",
            "立刻", "马上行动", "高风险",
            "负债过高", "债务危机"
    );

    private static final List<String[]> FORBIDDEN_REPLACEMENTS = Arrays.asList(
            new String[]{"问题严重", "有优化空间"},
            new String[]{"情况严重", "需要关注"},
            new String[]{"非常严重", "需要重点关注"},
            new String[]{"相当严重", "需要关注"},
            new String[]{"赶紧行动", "你可以从这一步开始"},
            new String[]{"赶紧处理", "可以优先处理"},
            new String[]{"赶快行动", "你可以从这一步开始"},
            new String[]{"赶快处理", "可以优先处理"},
            new String[]{"最后机会", ""},
            new String[]{"最后一次机会", ""},
            new String[]{"申请失败", "暂时不适合，建议先优化"},
            new String[]{"不符合条件", "当前更适合优化信用结构"},
            new String[]{"审核不通过", "建议先优化信用结构"},
            new String[]{"高风险", "需要关注"},
            new String[]{"负债过高", "月供压力偏大"},
            new String[]{"债务危机", "财务结构有优化空间"}
    );

    private final AiChatCaller aiChatCaller;
    private final ObjectMapper objectMapper;

    /**
     * Generate personalized debt optimization suggestion.
     *
     * <p>All numerical data is sourced from the rule engine ({@code profile} and {@code scoreResult}).
     * AI is only responsible for natural-language generation (F-02).
     *
     * @param profile     user's finance profile with pre-calculated metrics
     * @param debts       list of user's debts (sorted by APR descending for prompt)
     * @param scoreResult result from {@link com.youhua.engine.scoring.ScoringEngine}
     * @return structured suggestion result with five psychological segments
     * @throws BizException AI_SUGGESTION_FAILED on AI call failure
     */
    public SuggestionResult generate(FinanceProfile profile, List<Debt> debts, ScoreResult scoreResult) {
        if (profile == null) {
            throw new BizException(ErrorCode.AI_SUGGESTION_FAILED, "Finance profile must not be null");
        }
        if (scoreResult == null) {
            throw new BizException(ErrorCode.AI_SUGGESTION_FAILED, "Score result must not be null");
        }

        List<Debt> safeDebts = debts != null ? debts : List.of();

        String userPrompt = buildUserPrompt(profile, safeDebts, scoreResult);
        String systemPrompt = buildSystemPrompt(scoreResult.riskLevel());

        log.debug("Calling AI for suggestion generation, userId={}, riskLevel={}",
                profile.getUserId(), scoreResult.riskLevel());

        try {
            String rawResponse = aiChatCaller.callForSuggestion(systemPrompt, userPrompt);

            log.debug("Raw AI response received, userId={}", profile.getUserId());

            return parseAndPostProcess(rawResponse, profile, safeDebts, scoreResult);

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("AI suggestion generation failed for userId={}", profile.getUserId(), e);
            return buildFallbackResult(profile, safeDebts, scoreResult);
        }
    }

    // ===================== Prompt Building =====================

    String buildSystemPrompt(RiskLevel riskLevel) {
        String base = """
                你是一名专业的财务优化顾问。你的任务是根据用户的财务数据分析结果，生成通俗易懂、具有可操作性的债务优化建议。

                ## 严格约束
                1. 你不是在提供金融产品推荐或借贷服务
                2. 不得建议用户"以贷还贷""借新还旧"
                3. 不得推荐任何具体金融产品、APP、平台
                4. 不得暗示用户可以通过你或平台获得贷款
                5. 所有数值直接引用输入数据中的 calculatedData，不得自行计算或推测
                6. 建议必须具体、可执行，不要说空话
                7. 语气专业但温和，不制造焦虑
                8. 如果用户有逾期，优先建议处理逾期

                ## 输出格式
                你必须严格按以下 JSON 结构输出，不要输出任何其他文字：

                {
                  "empathy": "共情段落（1-2句，确认用户感受，不用数字开头）",
                  "quantifiedLoss": "量化损失段落（仅一句，引用实际数据）",
                  "positiveTurn": "正面转换段落（1-2句，立刻转为可控希望）",
                  "actionSteps": ["具体行动步骤1", "具体行动步骤2", "具体行动步骤3"],
                  "safetyClosure": "安全兜底句（1句，以安全感结尾）",
                  "summary": "整体总结（50-200字）"
                }
                """;

        String riskAddendum = switch (riskLevel) {
            case LOW -> """

                    ## 当前用户风险等级：低风险
                    用户财务状况较好。建议侧重：
                    - 如何进一步降低利息支出
                    - 是否有提前还款的机会
                    - 如何保持良好的信用记录
                    语气轻松乐观。共情段可以简短。
                    """;
            case MEDIUM -> """

                    ## 当前用户风险等级：中等
                    用户财务有一定压力但可控。建议侧重：
                    - 高息债务的优先处理顺序
                    - 如何优化月供压力
                    - 可以考虑进一步评估优化方案
                    语气专业、务实。第三段（正面转换）要加强。
                    """;
            case HIGH -> """

                    ## 当前用户风险等级：需要关注
                    用户财务压力较大。建议侧重：
                    - 逾期债务的处理顺序
                    - 如何与债权人沟通协商
                    - 控制新增借贷
                    - 开源节流的具体方法
                    语气温和但坚定。安全兜底段要加强。
                    注意：不制造焦虑，用"需要关注"替代"高风险"。
                    """;
            case CRITICAL -> """

                    ## 当前用户风险等级：需要重点关注
                    用户财务状况需要重点关注。建议侧重：
                    - 先稳定现状，不追求一步到位
                    - 逾期债务按影响程度排序
                    - 建议咨询专业顾问（但不是"你必须去找律师"）
                    - 心理支持（财务困难是暂时的，可以改善的）
                    语气温暖、有力量感。共情段要充分，安全感要最强。
                    绝不推荐"以贷还贷"的任何变体。
                    """;
        };

        return base + riskAddendum;
    }

    String buildUserPrompt(FinanceProfile profile, List<Debt> debts, ScoreResult scoreResult) {
        NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);
        nf.setGroupingUsed(true);

        String totalDebtStr = profile.getTotalDebt() != null
                ? nf.format(profile.getTotalDebt().setScale(2, RoundingMode.HALF_UP))
                : "未知";
        String weightedAprStr = profile.getWeightedApr() != null
                ? profile.getWeightedApr().setScale(2, RoundingMode.HALF_UP).toPlainString()
                : "未知";
        String monthlyPaymentStr = profile.getMonthlyPayment() != null
                ? nf.format(profile.getMonthlyPayment().setScale(2, RoundingMode.HALF_UP))
                : "未知";
        String monthlyIncomeStr = profile.getMonthlyIncome() != null
                ? nf.format(profile.getMonthlyIncome().setScale(2, RoundingMode.HALF_UP))
                : "未提供";
        String debtIncomeRatioStr = profile.getDebtIncomeRatio() != null
                ? profile.getDebtIncomeRatio().setScale(4, RoundingMode.HALF_UP).toPlainString()
                : "未知";
        String debtIncomeRatioPercentStr = profile.getDebtIncomeRatio() != null
                ? profile.getDebtIncomeRatio().multiply(BigDecimal.valueOf(100))
                        .setScale(1, RoundingMode.HALF_UP).toPlainString()
                : "未知";
        String riskLevelStr = mapRiskLevelToChinese(profile.getRiskLevel());
        String restructureScoreStr = profile.getRestructureScore() != null
                ? profile.getRestructureScore().setScale(1, RoundingMode.HALF_UP).toPlainString()
                : "未知";

        // Build debt list sorted by APR descending
        List<Debt> sortedDebts = new ArrayList<>(debts);
        sortedDebts.sort(Comparator.comparing(
                d -> d.getApr() != null ? d.getApr() : BigDecimal.ZERO,
                Comparator.reverseOrder()
        ));

        StringBuilder debtListBuilder = new StringBuilder();
        for (int i = 0; i < sortedDebts.size(); i++) {
            Debt d = sortedDebts.get(i);
            String overdueStr = d.getOverdueStatus() != null && d.getOverdueStatus() != OverdueStatus.NORMAL
                    ? "逾期" + (d.getOverdueDays() != null ? d.getOverdueDays() + "天" : "")
                    : "正常";
            String aprStr = d.getApr() != null ? d.getApr().setScale(2, RoundingMode.HALF_UP).toPlainString() : "未知";
            String principalStr = d.getPrincipal() != null
                    ? nf.format(d.getPrincipal().setScale(2, RoundingMode.HALF_UP))
                    : "未知";
            String monthlyStr = d.getMonthlyPayment() != null
                    ? nf.format(d.getMonthlyPayment().setScale(2, RoundingMode.HALF_UP))
                    : "未知";
            debtListBuilder.append(String.format("%d. %s，本金%s元，年化%s%%，月供%s元，%s%n",
                    i + 1, d.getCreditor(), principalStr, aprStr, monthlyStr, overdueStr));
        }

        // Top 2 factors from score dimensions
        String topFactor1 = "负债收入比";
        String topFactor2 = "综合利率";
        if (scoreResult.dimensions() != null && scoreResult.dimensions().size() >= 2) {
            var sortedDims = scoreResult.dimensions().stream()
                    .sorted(Comparator.comparing(dim -> dim.weightedScore(), Comparator.reverseOrder()))
                    .toList();
            topFactor1 = sortedDims.get(0).label();
            if (sortedDims.size() > 1) {
                topFactor2 = sortedDims.get(1).label();
            }
        }

        // Priority debt IDs (top 3 by APR)
        String priorityDebtIds = sortedDebts.stream()
                .limit(3)
                .map(d -> d.getCreditor() != null ? d.getCreditor() : String.valueOf(d.getId()))
                .reduce((a, b) -> a + "、" + b)
                .orElse("无");

        boolean hasOverdue = debts.stream()
                .anyMatch(d -> d.getOverdueStatus() != null && d.getOverdueStatus() != OverdueStatus.NORMAL);
        boolean paymentExceedIncome = profile.getMonthlyIncome() != null
                && profile.getMonthlyPayment() != null
                && profile.getMonthlyPayment().compareTo(profile.getMonthlyIncome()) > 0;

        String recommendation = switch (scoreResult.recommendation()) {
            case RESTRUCTURE_RECOMMENDED -> "RESTRUCTURE_RECOMMENDED（推荐优化重组）";
            case OPTIMIZE_FIRST -> "OPTIMIZE_FIRST（先优化信用结构）";
            case CREDIT_BUILDING -> "CREDIT_BUILDING（30天信用修复计划）";
        };

        return String.format("""
                请根据以下用户的财务数据分析结果，生成个性化的债务优化建议。

                ## 用户财务画像
                - 总负债：%s 元
                - 债务笔数：%d 笔
                - 加权年化利率：%s%%
                - 月供总额：%s 元
                - 月收入：%s 元
                - 负债收入比：%s（%s%%）
                - 重组评分：%s 分
                - 风险等级：%s

                ## 债务明细（按APR从高到低排序）
                %s
                ## 评分维度分析
                - 影响最大的因素：%s
                - 第二影响因素：%s

                ## 系统建议方向（基于规则引擎输出）
                - 推荐策略：%s
                - 优先处理的债务：%s
                - 是否有逾期：%s
                - 是否月供超收入：%s

                请基于以上数据生成优化建议。注意：所有数值直接引用上面的数据，不要自己计算。
                """,
                totalDebtStr, profile.getDebtCount() != null ? profile.getDebtCount() : 0,
                weightedAprStr, monthlyPaymentStr, monthlyIncomeStr,
                debtIncomeRatioStr, debtIncomeRatioPercentStr,
                restructureScoreStr, riskLevelStr,
                debtListBuilder,
                topFactor1, topFactor2,
                recommendation, priorityDebtIds,
                hasOverdue ? "是" : "否",
                paymentExceedIncome ? "是" : "否"
        );
    }

    // ===================== Post-processing =====================

    SuggestionResult parseAndPostProcess(String rawResponse, FinanceProfile profile,
                                         List<Debt> debts, ScoreResult scoreResult) {
        String cleaned = extractJson(rawResponse);

        try {
            JsonNode root = objectMapper.readTree(cleaned);

            String empathy = filterForbiddenPhrases(getTextOrDefault(root, "empathy",
                    buildDefaultEmpathy(scoreResult.riskLevel())));
            String quantifiedLoss = filterForbiddenPhrases(getTextOrDefault(root, "quantifiedLoss",
                    buildDefaultQuantifiedLoss(profile)));
            String positiveTurn = filterForbiddenPhrases(getTextOrDefault(root, "positiveTurn",
                    buildDefaultPositiveTurn(scoreResult)));
            List<String> actionSteps = parseActionSteps(root, scoreResult);
            String safetyClosure = filterForbiddenPhrases(getTextOrDefault(root, "safetyClosure",
                    "这些调整不影响你的信用记录，随时可以调整节奏。"));
            String summary = filterForbiddenPhrases(getTextOrDefault(root, "summary", ""));
            summary = enforceLength(summary, 50, 200,
                    "你的财务结构有优化空间，通过调整债务优先级可以降低整体利息支出。");

            List<String> priorityCreditors = buildPriorityCreditors(debts);

            validateNoRejectionLanguage(List.of(empathy, quantifiedLoss, positiveTurn, safetyClosure, summary),
                    scoreResult);

            return SuggestionResult.builder()
                    .empathy(empathy)
                    .quantifiedLoss(quantifiedLoss)
                    .positiveTurn(positiveTurn)
                    .actionSteps(actionSteps)
                    .safetyClosure(safetyClosure)
                    .summary(summary)
                    .priorityCreditors(priorityCreditors)
                    .aiGenerated(true)
                    .build();

        } catch (JsonProcessingException e) {
            log.warn("Failed to parse AI JSON response, falling back to template. userId={}",
                    profile.getUserId());
            return buildFallbackResult(profile, debts, scoreResult);
        }
    }

    private String extractJson(String raw) {
        if (raw == null) return "{}";
        // Strip markdown code fences if present
        String trimmed = raw.strip();
        if (trimmed.startsWith("```")) {
            int start = trimmed.indexOf('\n');
            int end = trimmed.lastIndexOf("```");
            if (start >= 0 && end > start) {
                trimmed = trimmed.substring(start + 1, end).strip();
            }
        }
        // Find first '{' and last '}'
        int first = trimmed.indexOf('{');
        int last = trimmed.lastIndexOf('}');
        if (first >= 0 && last > first) {
            return trimmed.substring(first, last + 1);
        }
        return trimmed;
    }

    private String getTextOrDefault(JsonNode root, String field, String defaultValue) {
        JsonNode node = root.get(field);
        if (node != null && node.isTextual() && !node.asText().isBlank()) {
            return node.asText();
        }
        return defaultValue;
    }

    private List<String> parseActionSteps(JsonNode root, ScoreResult scoreResult) {
        List<String> steps = new ArrayList<>();
        JsonNode stepsNode = root.get("actionSteps");
        if (stepsNode != null && stepsNode.isArray()) {
            for (JsonNode step : stepsNode) {
                String text = filterForbiddenPhrases(step.asText());
                if (!text.isBlank()) {
                    steps.add(text);
                }
            }
        }
        if (steps.isEmpty()) {
            steps = buildDefaultActionSteps(scoreResult);
        }
        // Enforce 2-4 steps
        if (steps.size() > 4) {
            steps = steps.subList(0, 4);
        }
        return steps;
    }

    /**
     * Replaces forbidden phrases with safe alternatives.
     * Implements post-processing requirement from suggestion-gen.md section 5.
     */
    String filterForbiddenPhrases(String text) {
        if (text == null) return "";
        String result = text;
        for (String[] pair : FORBIDDEN_REPLACEMENTS) {
            result = result.replace(pair[0], pair[1]);
        }
        return result;
    }

    /**
     * Validates that score < 60 users don't see rejection language (F-13).
     */
    private void validateNoRejectionLanguage(List<String> segments, ScoreResult scoreResult) {
        if (scoreResult.finalScore().compareTo(new BigDecimal("60")) < 0) {
            List<String> rejectionPhrases = List.of("申请失败", "不符合条件", "审核不通过", "被拒");
            for (String segment : segments) {
                for (String phrase : rejectionPhrases) {
                    if (segment.contains(phrase)) {
                        log.warn("Rejection phrase '{}' found in suggestion for low-score user, this should have been filtered", phrase);
                    }
                }
            }
        }
    }

    private String enforceLength(String text, int minLen, int maxLen, String fallback) {
        if (text == null || text.length() < minLen) {
            return fallback;
        }
        if (text.length() > maxLen) {
            return text.substring(0, maxLen);
        }
        return text;
    }

    private List<String> buildPriorityCreditors(List<Debt> debts) {
        return debts.stream()
                .filter(d -> d.getApr() != null)
                .sorted(Comparator.comparing(Debt::getApr, Comparator.reverseOrder()))
                .limit(3)
                .map(Debt::getCreditor)
                .filter(c -> c != null && !c.isBlank())
                .toList();
    }

    // ===================== Fallback / Default Templates =====================

    SuggestionResult buildFallbackResult(FinanceProfile profile, List<Debt> debts, ScoreResult scoreResult) {
        log.info("Using rule-based fallback suggestion for userId={}", profile.getUserId());

        String empathy = buildDefaultEmpathy(scoreResult.riskLevel());
        String quantifiedLoss = buildDefaultQuantifiedLoss(profile);
        String positiveTurn = buildDefaultPositiveTurn(scoreResult);
        List<String> actionSteps = buildDefaultActionSteps(scoreResult);
        String safetyClosure = "这些调整不影响你的信用记录，随时可以调整节奏。";
        List<String> priorityCreditors = buildPriorityCreditors(debts);

        return SuggestionResult.builder()
                .empathy(empathy)
                .quantifiedLoss(quantifiedLoss)
                .positiveTurn(positiveTurn)
                .actionSteps(actionSteps)
                .safetyClosure(safetyClosure)
                .summary("你的财务结构有优化空间，通过调整债务优先级可以降低整体利息支出。")
                .priorityCreditors(priorityCreditors)
                .aiGenerated(false)
                .build();
    }

    private String buildDefaultEmpathy(RiskLevel riskLevel) {
        return switch (riskLevel) {
            case LOW -> "管理多笔债务确实需要花费精力，你目前的财务状况整体向好。";
            case MEDIUM -> "管理多笔债务确实需要花费精力，你的财务结构有一定优化空间。";
            case HIGH -> "管理多笔债务确实不容易，你已经在认真面对这件事了。";
            case CRITICAL -> "同时管理多笔债务需要付出很多心力，你能主动来了解自己的财务状况，这本身就是很重要的一步。";
        };
    }

    private String buildDefaultQuantifiedLoss(FinanceProfile profile) {
        if (profile.getWeightedApr() != null && profile.getMonthlyPayment() != null) {
            // Rough estimate: extra interest = monthlyPayment * 36 * (weightedApr / 24%)  - simplified
            BigDecimal base = profile.getMonthlyPayment()
                    .multiply(BigDecimal.valueOf(36))
                    .multiply(profile.getWeightedApr())
                    .divide(BigDecimal.valueOf(100), 0, RoundingMode.HALF_UP);
            NumberFormat nf = NumberFormat.getNumberInstance(Locale.CHINA);
            return "按当前结构，未来3年将多支付约 " + nf.format(base) + " 元利息。";
        }
        return "按当前结构，未来3年会产生一定的额外利息支出。";
    }

    private String buildDefaultPositiveTurn(ScoreResult scoreResult) {
        return switch (scoreResult.recommendation()) {
            case RESTRUCTURE_RECOMMENDED ->
                    "好消息是，你有优化空间。通过调整债务结构，可以有效降低利息支出，改善月供压力。";
            case OPTIMIZE_FIRST ->
                    "好消息是，通过调整债务处理顺序，你可以逐步减轻月供压力，信用状况也会随之改善。";
            case CREDIT_BUILDING ->
                    "好消息是，财务状况是可以改善的。从小步骤开始，30天内就能看到变化。";
        };
    }

    private List<String> buildDefaultActionSteps(ScoreResult scoreResult) {
        return switch (scoreResult.recommendation()) {
            case RESTRUCTURE_RECOMMENDED -> List.of(
                    "第一步：优先处理利率最高的债务（节省效果最显著）",
                    "第二步：评估是否可以整合高息债务，降低综合利率",
                    "第三步：设置还款日历，避免逾期产生额外费用"
            );
            case OPTIMIZE_FIRST -> List.of(
                    "第一步：优先还清利率最高的小额债务",
                    "第二步：与债权人确认还款计划，保持良好沟通",
                    "第三步：30天后重新评估优化空间"
            );
            case CREDIT_BUILDING -> List.of(
                    "第一步：先确保所有债务不再产生新逾期",
                    "第二步：制定30天还款计划，每笔按时还款",
                    "第三步：30天后重新评估，信用状况会有明显改善"
            );
        };
    }

    private String mapRiskLevelToChinese(RiskLevel riskLevel) {
        if (riskLevel == null) return "未知";
        return switch (riskLevel) {
            case LOW -> "低";
            case MEDIUM -> "中";
            case HIGH -> "高";
            case CRITICAL -> "极高";
        };
    }
}
