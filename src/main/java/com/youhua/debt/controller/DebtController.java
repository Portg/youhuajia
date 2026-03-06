package com.youhua.debt.controller;

import com.youhua.debt.dto.request.CreateDebtRequest;
import com.youhua.debt.dto.request.ListDebtsRequest;
import com.youhua.debt.dto.request.UpdateDebtRequest;
import com.youhua.debt.dto.response.DebtResponse;
import com.youhua.debt.dto.response.ListDebtsResponse;
import com.youhua.debt.service.DebtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "debts", description = "债务资源")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class DebtController {

    private final DebtService debtService;

    @Operation(summary = "List - 查询债务列表")
    @GetMapping("/debts")
    public ListDebtsResponse listDebts(ListDebtsRequest request) {
        return debtService.listDebts(request);
    }

    @Operation(summary = "Create - 创建债务（草稿状态）")
    @PostMapping("/debts")
    public DebtResponse createDebt(@Valid @RequestBody CreateDebtRequest request) {
        return debtService.createDebt(request);
    }

    @Operation(summary = "Get - 查询债务详情")
    @GetMapping("/debts/{debtId}")
    public DebtResponse getDebt(@PathVariable Long debtId) {
        return debtService.getDebt(debtId);
    }

    @Operation(summary = "Update - 部分更新债务（PATCH + updateMask，AIP-134）")
    @PatchMapping("/debts/{debtId}")
    public DebtResponse updateDebt(@PathVariable Long debtId,
                                   @Valid @RequestBody UpdateDebtRequest request) {
        return debtService.updateDebt(debtId, request);
    }

    @Operation(summary = "Delete - 删除债务（逻辑删除）")
    @DeleteMapping("/debts/{debtId}")
    public void deleteDebt(@PathVariable Long debtId) {
        debtService.deleteDebt(debtId);
    }

    @Operation(summary = "确认债务数据（触发 APR 计算）")
    @PostMapping("/debts/{debtId}:confirm")
    public DebtResponse confirmDebt(@PathVariable Long debtId) {
        return debtService.confirmDebt(debtId);
    }

    @Operation(summary = "将债务纳入画像计算")
    @PostMapping("/debts/{debtId}:includeInProfile")
    public DebtResponse includeDebtInProfile(@PathVariable Long debtId) {
        return debtService.includeDebtInProfile(debtId);
    }
}
