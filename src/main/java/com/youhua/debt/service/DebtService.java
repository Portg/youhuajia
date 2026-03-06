package com.youhua.debt.service;

import com.youhua.debt.dto.request.CreateDebtRequest;
import com.youhua.debt.dto.request.ListDebtsRequest;
import com.youhua.debt.dto.request.UpdateDebtRequest;
import com.youhua.debt.dto.response.DebtResponse;
import com.youhua.debt.dto.response.ListDebtsResponse;

public interface DebtService {

    ListDebtsResponse listDebts(ListDebtsRequest request);

    DebtResponse createDebt(CreateDebtRequest request);

    DebtResponse getDebt(Long debtId);

    DebtResponse updateDebt(Long debtId, UpdateDebtRequest request);

    void deleteDebt(Long debtId);

    DebtResponse confirmDebt(Long debtId);

    DebtResponse includeDebtInProfile(Long debtId);
}
