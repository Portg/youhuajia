package com.youhua.profile.controller;

import com.youhua.profile.dto.request.CreateConsultationRequest;
import com.youhua.profile.dto.response.ConsultationResponse;
import com.youhua.profile.service.ConsultationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Tag(name = "consultations", description = "咨询意向收集")
@RestController
@RequestMapping("/api/v1/consultations")
@RequiredArgsConstructor
public class ConsultationController {

    private final ConsultationService consultationService;

    @Operation(summary = "Create - 提交咨询意向")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsultationResponse create(@Valid @RequestBody CreateConsultationRequest request) {
        return consultationService.create(request);
    }
}
