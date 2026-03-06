package com.youhua.ai.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * AI-generated debt optimization suggestion result.
 *
 * <p>Contains five structured segments following the user-journey.md psychological path:
 * empathy → quantified loss → positive turn → action plan → safety closing.
 *
 * <p>All monetary values in this DTO come from the rule engine, not from AI calculation (F-02).
 */
@Getter
@Builder
public class SuggestionResult {

    /**
     * Segment 1: Empathy — acknowledges user's situation (1-2 sentences).
     */
    private final String empathy;

    /**
     * Segment 2: Quantified loss — one-time statement of extra cost over 3 years.
     */
    private final String quantifiedLoss;

    /**
     * Segment 3: Positive turn — immediately transitions to hope/opportunity.
     */
    private final String positiveTurn;

    /**
     * Segment 4: Action plan — 2-4 concrete, independently executable steps.
     */
    private final List<String> actionSteps;

    /**
     * Segment 5: Safety closing — reassurance sentence, always ends on safety.
     */
    private final String safetyClosure;

    /**
     * Priority creditor names to handle first (derived from rule engine BIZ_002 output).
     */
    private final List<String> priorityCreditors;

    /**
     * Overall summary (50-200 characters).
     */
    private final String summary;

    /**
     * Whether this result was generated via AI or fell back to a rule-based template.
     */
    private final boolean aiGenerated;
}
