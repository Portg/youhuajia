package com.youhua.profile.controller;

import com.youhua.profile.dto.response.FinanceProfileResponse;
import com.youhua.profile.service.FinanceProfileService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "finance-profiles", description = "财务画像资源")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class FinanceProfileController {

    private final FinanceProfileService financeProfileService;

    @Operation(summary = "Get - 获取当前用户的财务画像")
    @GetMapping("/finance-profiles/mine")
    public FinanceProfileResponse getFinanceProfile() {
        return financeProfileService.getFinanceProfile();
    }

    @Operation(summary = "触发画像重新计算（自定义方法）")
    @PostMapping("/finance-profiles/mine:calculate")
    public FinanceProfileResponse calculateFinanceProfile() {
        return financeProfileService.calculateFinanceProfile();
    }
}
