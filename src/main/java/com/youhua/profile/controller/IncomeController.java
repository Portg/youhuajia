package com.youhua.profile.controller;

import com.youhua.profile.dto.request.BatchCreateIncomesRequest;
import com.youhua.profile.dto.response.IncomeResponse;
import com.youhua.profile.service.IncomeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@Tag(name = "incomes", description = "收入资源")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class IncomeController {

    private final IncomeService incomeService;

    @Operation(summary = "批量创建收入记录（自定义方法，AIP-136）")
    @PostMapping("/incomes:batchCreate")
    public Map<String, List<IncomeResponse>> batchCreateIncomes(
            @Valid @RequestBody BatchCreateIncomesRequest request) {
        return Map.of("incomes", incomeService.batchCreateIncomes(request));
    }
}
