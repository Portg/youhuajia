package com.youhua.report.dto;

import java.util.List;

/**
 * Layer 3: AI-generated debt optimization suggestion segments.
 *
 * <p>This record is null when AI suggestion generation fails (graceful degradation).
 * The frontend should render a fallback message when this is absent.
 */
public record SuggestionLayer(
        String empathy,
        String quantifiedLoss,
        String positiveTurn,
        List<String> actionSteps,
        String safetyClosure,
        String summary,
        List<String> priorityCreditors,
        boolean aiGenerated
) {}
