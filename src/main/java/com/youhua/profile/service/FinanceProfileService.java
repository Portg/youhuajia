package com.youhua.profile.service;

import com.youhua.profile.dto.response.FinanceProfileResponse;

public interface FinanceProfileService {

    FinanceProfileResponse getFinanceProfile();

    FinanceProfileResponse calculateFinanceProfile();

    /**
     * Recalculates the finance profile for the given user without requiring an HTTP request context.
     * Used by async event listeners (e.g. ProfileRecalculationListener) where
     * RequestContextHolder is not available.
     */
    void recalculateForUser(Long userId);
}
