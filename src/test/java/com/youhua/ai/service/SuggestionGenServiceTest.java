package com.youhua.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.ai.dto.SuggestionResult;
import com.youhua.common.exception.BizException;
import com.youhua.debt.entity.Debt;
import com.youhua.debt.enums.OverdueStatus;
import com.youhua.engine.scoring.ScoringEngine.DimensionDetail;
import com.youhua.engine.scoring.ScoringEngine.Recommendation;
import com.youhua.engine.scoring.ScoringEngine.ScoreResult;
import com.youhua.profile.entity.FinanceProfile;
import com.youhua.profile.enums.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Unit tests for SuggestionGenService.
 *
 * <p>Mocks AiChatCaller — no actual AI calls are made.
 * Covers: five-segment generation, forbidden phrase filtering, AI panic language post-processing,
 * score &lt; 60 path, fallback on AI failure, null input validation.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("SuggestionGenService Tests")
class SuggestionGenServiceTest {

    @Mock
    private AiChatCaller aiChatCaller;

    private SuggestionGenService service;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new SuggestionGenService(aiChatCaller, objectMapper);
    }

    // ===================== Test Data Builders =====================

    private FinanceProfile buildProfile(BigDecimal totalDebt, BigDecimal monthlyPayment,
                                        BigDecimal monthlyIncome, BigDecimal weightedApr,
                                        BigDecimal debtIncomeRatio, BigDecimal restructureScore,
                                        RiskLevel riskLevel) {
        FinanceProfile p = new FinanceProfile();
        p.setUserId(1001L);
        p.setTotalDebt(totalDebt);
        p.setDebtCount(2);
        p.setMonthlyPayment(monthlyPayment);
        p.setMonthlyIncome(monthlyIncome);
        p.setWeightedApr(weightedApr);
        p.setDebtIncomeRatio(debtIncomeRatio);
        p.setRestructureScore(restructureScore);
        p.setRiskLevel(riskLevel);
        return p;
    }

    private ScoreResult buildScoreResult(BigDecimal score, RiskLevel riskLevel,
                                          Recommendation recommendation) {
        List<DimensionDetail> dims = List.of(
                new DimensionDetail("debtIncomeRatio", "负债收入比",
                        new BigDecimal("0.45"), new BigDecimal("70"), new BigDecimal("0.30"),
                        new BigDecimal("21.00")),
                new DimensionDetail("weightedApr", "综合利率",
                        new BigDecimal("18.0"), new BigDecimal("75"), new BigDecimal("0.25"),
                        new BigDecimal("18.75"))
        );
        return new ScoreResult(score, riskLevel, recommendation,
                "好消息是，你有优化空间", "利率模拟器（Page 6）", dims, LocalDateTime.now());
    }

    private Debt buildDebt(String creditor, BigDecimal principal, BigDecimal apr,
                            BigDecimal monthlyPayment, OverdueStatus overdueStatus) {
        Debt d = new Debt();
        d.setCreditor(creditor);
        d.setPrincipal(principal);
        d.setApr(apr);
        d.setMonthlyPayment(monthlyPayment);
        d.setOverdueStatus(overdueStatus);
        return d;
    }

    private void mockAiResponse(String responseContent) {
        when(aiChatCaller.callForSuggestion(anyString(), anyString())).thenReturn(responseContent);
    }

    private void mockAiException(RuntimeException ex) {
        when(aiChatCaller.callForSuggestion(anyString(), anyString())).thenThrow(ex);
    }

    // ===================== Normal Path: Five-Segment Generation =====================

    @Test
    @DisplayName("SG-N01: should_return_five_segments_when_ai_returns_valid_json")
    void should_return_five_segments_when_ai_returns_valid_json() {
        String validJson = """
                {
                  "empathy": "管理多笔债务确实需要花费精力，你已经很认真地面对了。",
                  "quantifiedLoss": "按当前结构，未来3年将多支付约 24,000 元利息。",
                  "positiveTurn": "好消息是，通过调整债务优先级，你可以显著降低利息支出。",
                  "actionSteps": [
                    "第一步：优先处理招商银行信用卡（年化最高，节省效果最显著）",
                    "第二步：评估是否可以申请等额本息转换",
                    "第三步：设置还款提醒，避免逾期"
                  ],
                  "safetyClosure": "这些调整不影响你的信用记录，随时可以调整节奏。",
                  "summary": "你的月供压力偏大，但通过优先处理高息债务，有机会在90天内改善财务结构。"
                }
                """;

        mockAiResponse(validJson);

        FinanceProfile profile = buildProfile(
                new BigDecimal("80000"), new BigDecimal("5000"), new BigDecimal("12000"),
                new BigDecimal("18.0"), new BigDecimal("0.4167"), new BigDecimal("72"),
                RiskLevel.MEDIUM);
        List<Debt> debts = List.of(
                buildDebt("招商银行", new BigDecimal("50000"), new BigDecimal("22.0"),
                        new BigDecimal("3000"), OverdueStatus.NORMAL),
                buildDebt("花呗", new BigDecimal("30000"), new BigDecimal("14.0"),
                        new BigDecimal("2000"), OverdueStatus.NORMAL)
        );
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("72.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        SuggestionResult result = service.generate(profile, debts, scoreResult);

        assertThat(result).isNotNull();
        assertThat(result.isAiGenerated()).isTrue();
        assertThat(result.getEmpathy()).isNotBlank();
        assertThat(result.getQuantifiedLoss()).isNotBlank();
        assertThat(result.getPositiveTurn()).isNotBlank();
        assertThat(result.getActionSteps()).isNotNull().hasSize(3);
        assertThat(result.getSafetyClosure()).isNotBlank();
        assertThat(result.getSummary()).isNotBlank();
        assertThat(result.getPriorityCreditors()).contains("招商银行");
    }

    @Test
    @DisplayName("SG-N02: should_sort_priority_creditors_by_apr_descending")
    void should_sort_priority_creditors_by_apr_descending() {
        mockAiResponse("""
                {
                  "empathy": "管理多笔债务确实需要花费精力。",
                  "quantifiedLoss": "按当前结构，未来3年将多支付约 20,000 元利息。",
                  "positiveTurn": "好消息是，你有优化空间。",
                  "actionSteps": ["第一步：优先处理高息债务"],
                  "safetyClosure": "这些调整不影响你的信用记录，随时可以调整节奏。",
                  "summary": "你的财务结构有优化空间，通过调整债务优先级可以降低整体利息支出。"
                }
                """);

        FinanceProfile profile = buildProfile(
                new BigDecimal("150000"), new BigDecimal("8000"), new BigDecimal("15000"),
                new BigDecimal("20.0"), new BigDecimal("0.533"), new BigDecimal("65"),
                RiskLevel.MEDIUM);
        List<Debt> debts = List.of(
                buildDebt("平安银行", new BigDecimal("30000"), new BigDecimal("14.4"),
                        new BigDecimal("1500"), OverdueStatus.NORMAL),
                buildDebt("360借条", new BigDecimal("20000"), new BigDecimal("36.0"),
                        new BigDecimal("2000"), OverdueStatus.NORMAL),
                buildDebt("微粒贷", new BigDecimal("50000"), new BigDecimal("18.0"),
                        new BigDecimal("2500"), OverdueStatus.NORMAL),
                buildDebt("信用卡", new BigDecimal("50000"), new BigDecimal("20.0"),
                        new BigDecimal("2000"), OverdueStatus.NORMAL)
        );
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("65.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        SuggestionResult result = service.generate(profile, debts, scoreResult);

        assertThat(result.getPriorityCreditors()).containsExactly("360借条", "信用卡", "微粒贷");
    }

    // ===================== Forbidden Phrase Detection & Replacement =====================

    @Test
    @DisplayName("SG-F01: should_replace_panic_phrase_when_ai_returns_wenti_yanzhong")
    void should_replace_panic_phrase_when_ai_returns_wenti_yanzhong() {
        String input = "你的债务问题严重，需要立刻处理。";
        String filtered = service.filterForbiddenPhrases(input);

        assertThat(filtered).doesNotContain("问题严重");
        assertThat(filtered).contains("有优化空间");
    }

    @Test
    @DisplayName("SG-F02: should_replace_ganjin_xingdong_with_safe_alternative")
    void should_replace_ganjin_xingdong_with_safe_alternative() {
        String input = "赶紧行动，不然情况会更糟。";
        String filtered = service.filterForbiddenPhrases(input);

        assertThat(filtered).doesNotContain("赶紧行动");
        assertThat(filtered).contains("你可以从这一步开始");
    }

    @Test
    @DisplayName("SG-F03: should_replace_zuihou_jihui_phrase_from_ai_output")
    void should_replace_zuihou_jihui_phrase_from_ai_output() {
        String input = "这是最后机会，必须赶快行动。";
        String filtered = service.filterForbiddenPhrases(input);

        assertThat(filtered).doesNotContain("最后机会");
        assertThat(filtered).doesNotContain("赶快行动");
    }

    @Test
    @DisplayName("SG-F04: should_filter_forbidden_phrases_when_ai_returns_panic_text_in_empathy")
    void should_filter_forbidden_phrases_when_ai_returns_panic_text_in_empathy() {
        String panicJson = """
                {
                  "empathy": "你的债务问题严重，这是最后机会了！",
                  "quantifiedLoss": "按当前结构，未来3年将多支付约 50,000 元利息。",
                  "positiveTurn": "好消息是，你有优化空间。",
                  "actionSteps": ["赶紧行动，处理高息债务"],
                  "safetyClosure": "这些调整不影响你的信用记录，随时可以调整节奏。",
                  "summary": "你的高风险负债需要立刻处理。"
                }
                """;

        mockAiResponse(panicJson);

        FinanceProfile profile = buildProfile(
                new BigDecimal("200000"), new BigDecimal("15000"), new BigDecimal("18000"),
                new BigDecimal("28.0"), new BigDecimal("0.833"), new BigDecimal("45"),
                RiskLevel.HIGH);
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("45.00"),
                RiskLevel.HIGH, Recommendation.OPTIMIZE_FIRST);

        SuggestionResult result = service.generate(profile, List.of(), scoreResult);

        assertThat(result.getEmpathy()).doesNotContain("问题严重");
        assertThat(result.getEmpathy()).doesNotContain("最后机会");
        assertThat(result.getActionSteps().get(0)).doesNotContain("赶紧行动");
    }

    // ===================== Score < 60: No Rejection Language =====================

    @Test
    @DisplayName("SG-L01: should_not_contain_rejection_language_when_score_below_60")
    void should_not_contain_rejection_language_when_score_below_60() {
        String rejectionJson = """
                {
                  "empathy": "你的情况需要关注。",
                  "quantifiedLoss": "按当前结构，未来3年将多支付约 30,000 元利息。",
                  "positiveTurn": "当前更适合优化信用结构，先从小步骤开始。",
                  "actionSteps": [
                    "申请失败不代表没有出路，先优化信用",
                    "不符合条件的债务先排期处理"
                  ],
                  "safetyClosure": "这些调整不影响你的信用记录，随时可以调整节奏。",
                  "summary": "你的财务结构有提升空间，30天行动计划可以改善现状。"
                }
                """;

        mockAiResponse(rejectionJson);

        FinanceProfile profile = buildProfile(
                new BigDecimal("120000"), new BigDecimal("12000"), new BigDecimal("14000"),
                new BigDecimal("30.0"), new BigDecimal("0.857"), new BigDecimal("35"),
                RiskLevel.CRITICAL);
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("35.00"),
                RiskLevel.CRITICAL, Recommendation.CREDIT_BUILDING);

        SuggestionResult result = service.generate(profile, List.of(), scoreResult);

        List<String> allText = List.of(
                result.getEmpathy(),
                result.getQuantifiedLoss(),
                result.getPositiveTurn(),
                result.getSafetyClosure(),
                result.getSummary()
        );
        for (String text : allText) {
            assertThat(text).doesNotContain("申请失败");
            assertThat(text).doesNotContain("不符合条件");
            assertThat(text).doesNotContain("审核不通过");
        }
        for (String step : result.getActionSteps()) {
            assertThat(step).doesNotContain("申请失败");
            assertThat(step).doesNotContain("不符合条件");
        }
    }

    @Test
    @DisplayName("SG-L02: should_use_credit_building_template_when_score_below_40")
    void should_use_credit_building_template_when_score_below_40() {
        mockAiException(new RuntimeException("AI service unavailable"));

        FinanceProfile profile = buildProfile(
                new BigDecimal("180000"), new BigDecimal("18000"), new BigDecimal("16000"),
                new BigDecimal("36.0"), new BigDecimal("1.125"), new BigDecimal("20"),
                RiskLevel.CRITICAL);
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("20.00"),
                RiskLevel.CRITICAL, Recommendation.CREDIT_BUILDING);

        SuggestionResult result = service.generate(profile, List.of(), scoreResult);

        assertThat(result.isAiGenerated()).isFalse();
        assertThat(result.getEmpathy()).doesNotContain("申请失败");
        assertThat(result.getEmpathy()).doesNotContain("不符合条件");
        assertThat(result.getPositiveTurn()).contains("好消息");
        assertThat(result.getSafetyClosure()).isNotBlank();
    }

    // ===================== AI Failure Fallback =====================

    @Test
    @DisplayName("SG-E01: should_return_fallback_result_when_ai_throws_exception")
    void should_return_fallback_result_when_ai_throws_exception() {
        mockAiException(new RuntimeException("Connection timeout"));

        FinanceProfile profile = buildProfile(
                new BigDecimal("80000"), new BigDecimal("5000"), new BigDecimal("12000"),
                new BigDecimal("18.0"), new BigDecimal("0.4167"), new BigDecimal("72"),
                RiskLevel.MEDIUM);
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("72.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        SuggestionResult result = service.generate(profile, List.of(), scoreResult);

        assertThat(result).isNotNull();
        assertThat(result.isAiGenerated()).isFalse();
        assertThat(result.getEmpathy()).isNotBlank();
        assertThat(result.getQuantifiedLoss()).isNotBlank();
        assertThat(result.getPositiveTurn()).isNotBlank();
        assertThat(result.getActionSteps()).isNotNull().isNotEmpty();
        assertThat(result.getSafetyClosure()).isNotBlank();
    }

    @Test
    @DisplayName("SG-E02: should_return_fallback_result_when_ai_returns_malformed_json")
    void should_return_fallback_result_when_ai_returns_malformed_json() {
        mockAiResponse("这是无效的非JSON响应文本");

        FinanceProfile profile = buildProfile(
                new BigDecimal("80000"), new BigDecimal("5000"), new BigDecimal("12000"),
                new BigDecimal("18.0"), new BigDecimal("0.4167"), new BigDecimal("72"),
                RiskLevel.MEDIUM);
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("72.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        SuggestionResult result = service.generate(profile, List.of(), scoreResult);

        assertThat(result).isNotNull();
        assertThat(result.isAiGenerated()).isFalse();
    }

    @Test
    @DisplayName("SG-E03: should_throw_biz_exception_when_profile_is_null")
    void should_throw_biz_exception_when_profile_is_null() {
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("72.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        assertThatThrownBy(() -> service.generate(null, List.of(), scoreResult))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("SG-E04: should_throw_biz_exception_when_score_result_is_null")
    void should_throw_biz_exception_when_score_result_is_null() {
        FinanceProfile profile = buildProfile(
                new BigDecimal("80000"), new BigDecimal("5000"), new BigDecimal("12000"),
                new BigDecimal("18.0"), new BigDecimal("0.4167"), new BigDecimal("72"),
                RiskLevel.MEDIUM);

        assertThatThrownBy(() -> service.generate(profile, List.of(), null))
                .isInstanceOf(BizException.class);
    }

    // ===================== Post-processing: JSON with Code Fences =====================

    @Test
    @DisplayName("SG-P01: should_parse_json_wrapped_in_markdown_code_fence")
    void should_parse_json_wrapped_in_markdown_code_fence() {
        String fencedJson = """
                ```json
                {
                  "empathy": "管理多笔债务确实需要花费精力。",
                  "quantifiedLoss": "按当前结构，未来3年将多支付约 18,000 元利息。",
                  "positiveTurn": "好消息是，你有优化空间。",
                  "actionSteps": ["第一步：优先处理高息债务"],
                  "safetyClosure": "这些调整不影响你的信用记录，随时可以调整节奏。",
                  "summary": "你的财务结构有优化空间，通过调整债务优先级可以降低整体利息支出。"
                }
                ```
                """;

        mockAiResponse(fencedJson);

        FinanceProfile profile = buildProfile(
                new BigDecimal("60000"), new BigDecimal("4000"), new BigDecimal("10000"),
                new BigDecimal("15.0"), new BigDecimal("0.40"), new BigDecimal("75"),
                RiskLevel.MEDIUM);
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("75.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        SuggestionResult result = service.generate(profile, List.of(), scoreResult);

        assertThat(result.isAiGenerated()).isTrue();
        assertThat(result.getEmpathy()).isEqualTo("管理多笔债务确实需要花费精力。");
    }

    // ===================== Action Steps Boundary: Truncate to 4 Steps =====================

    @Test
    @DisplayName("SG-P02: should_truncate_action_steps_to_max_4_when_ai_returns_more")
    void should_truncate_action_steps_to_max_4_when_ai_returns_more() {
        String jsonWithManySteps = """
                {
                  "empathy": "管理多笔债务确实需要花费精力。",
                  "quantifiedLoss": "按当前结构，未来3年将多支付约 20,000 元利息。",
                  "positiveTurn": "好消息是，你有优化空间。",
                  "actionSteps": [
                    "步骤1：处理高息债务",
                    "步骤2：设置还款计划",
                    "步骤3：联系债权人",
                    "步骤4：申请利率优惠",
                    "步骤5：评估整合方案"
                  ],
                  "safetyClosure": "这些调整不影响你的信用记录，随时可以调整节奏。",
                  "summary": "你的财务结构有优化空间，通过调整债务优先级可以降低整体利息支出。"
                }
                """;

        mockAiResponse(jsonWithManySteps);

        FinanceProfile profile = buildProfile(
                new BigDecimal("80000"), new BigDecimal("5000"), new BigDecimal("12000"),
                new BigDecimal("18.0"), new BigDecimal("0.4167"), new BigDecimal("72"),
                RiskLevel.MEDIUM);
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("72.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        SuggestionResult result = service.generate(profile, List.of(), scoreResult);

        assertThat(result.getActionSteps()).hasSizeLessThanOrEqualTo(4);
    }

    // ===================== Prompt Building =====================

    @Test
    @DisplayName("SG-P03: should_include_all_required_fields_in_user_prompt")
    void should_include_all_required_fields_in_user_prompt() {
        FinanceProfile profile = buildProfile(
                new BigDecimal("80000"), new BigDecimal("5000"), new BigDecimal("12000"),
                new BigDecimal("18.0"), new BigDecimal("0.4167"), new BigDecimal("72"),
                RiskLevel.MEDIUM);
        List<Debt> debts = List.of(
                buildDebt("招商银行", new BigDecimal("50000"), new BigDecimal("22.0"),
                        new BigDecimal("3000"), OverdueStatus.NORMAL)
        );
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("72.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        String prompt = service.buildUserPrompt(profile, debts, scoreResult);

        assertThat(prompt).contains("总负债");
        assertThat(prompt).contains("债务笔数");
        assertThat(prompt).contains("加权年化利率");
        assertThat(prompt).contains("月供总额");
        assertThat(prompt).contains("负债收入比");
        assertThat(prompt).contains("重组评分");
        assertThat(prompt).contains("招商银行");
        assertThat(prompt).contains("RESTRUCTURE_RECOMMENDED");
    }

    @Test
    @DisplayName("SG-P04: should_mark_overdue_debts_in_prompt_when_debt_has_overdue_status")
    void should_mark_overdue_debts_in_prompt_when_debt_has_overdue_status() {
        FinanceProfile profile = buildProfile(
                new BigDecimal("80000"), new BigDecimal("5000"), new BigDecimal("12000"),
                new BigDecimal("18.0"), new BigDecimal("0.4167"), new BigDecimal("45"),
                RiskLevel.HIGH);
        List<Debt> debts = List.of(
                buildDebt("360借条", new BigDecimal("30000"), new BigDecimal("36.0"),
                        new BigDecimal("3000"), OverdueStatus.OVERDUE_30)
        );
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("45.00"),
                RiskLevel.HIGH, Recommendation.OPTIMIZE_FIRST);

        String prompt = service.buildUserPrompt(profile, debts, scoreResult);

        assertThat(prompt).contains("逾期");
        assertThat(prompt).contains("是否有逾期：是");
    }

    @Test
    @DisplayName("SG-P05: should_use_high_risk_system_prompt_when_risk_level_is_high")
    void should_use_high_risk_system_prompt_when_risk_level_is_high() {
        String systemPrompt = service.buildSystemPrompt(RiskLevel.HIGH);

        assertThat(systemPrompt).contains("需要关注");
        assertThat(systemPrompt).contains("用\"需要关注\"替代\"高风险\"");
    }

    @Test
    @DisplayName("SG-P06: should_use_critical_risk_system_prompt_when_risk_level_is_critical")
    void should_use_critical_risk_system_prompt_when_risk_level_is_critical() {
        String systemPrompt = service.buildSystemPrompt(RiskLevel.CRITICAL);

        assertThat(systemPrompt).contains("重点关注");
        assertThat(systemPrompt).contains("以贷还贷");
    }

    // ===================== Null/Empty Debts =====================

    @Test
    @DisplayName("SG-N03: should_handle_null_debts_list_gracefully")
    void should_handle_null_debts_list_gracefully() {
        mockAiResponse("""
                {
                  "empathy": "管理多笔债务确实需要花费精力。",
                  "quantifiedLoss": "按当前结构，未来3年将多支付约 20,000 元利息。",
                  "positiveTurn": "好消息是，你有优化空间。",
                  "actionSteps": ["第一步：优先处理高息债务"],
                  "safetyClosure": "这些调整不影响你的信用记录，随时可以调整节奏。",
                  "summary": "你的财务结构有优化空间，通过调整债务优先级可以降低整体利息支出。"
                }
                """);

        FinanceProfile profile = buildProfile(
                new BigDecimal("80000"), new BigDecimal("5000"), new BigDecimal("12000"),
                new BigDecimal("18.0"), new BigDecimal("0.4167"), new BigDecimal("72"),
                RiskLevel.MEDIUM);
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("72.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        SuggestionResult result = service.generate(profile, null, scoreResult);

        assertThat(result).isNotNull();
        assertThat(result.getPriorityCreditors()).isEmpty();
    }

    @Test
    @DisplayName("SG-N04: should_use_no_income_message_in_prompt_when_monthly_income_is_null")
    void should_use_no_income_message_in_prompt_when_monthly_income_is_null() {
        FinanceProfile profile = buildProfile(
                new BigDecimal("80000"), new BigDecimal("5000"), null,
                new BigDecimal("18.0"), null, new BigDecimal("72"),
                RiskLevel.MEDIUM);
        ScoreResult scoreResult = buildScoreResult(new BigDecimal("72.00"),
                RiskLevel.MEDIUM, Recommendation.RESTRUCTURE_RECOMMENDED);

        String prompt = service.buildUserPrompt(profile, List.of(), scoreResult);

        assertThat(prompt).contains("未提供");
    }
}
