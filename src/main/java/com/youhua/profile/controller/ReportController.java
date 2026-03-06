package com.youhua.profile.controller;

import com.youhua.profile.dto.response.ListReportsResponse;
import com.youhua.profile.dto.response.ReportResponse;
import com.youhua.profile.service.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "reports", description = "优化报告资源")
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @Operation(summary = "List - 报告历史列表")
    @GetMapping("/reports")
    public ListReportsResponse listReports(
            @RequestParam(defaultValue = "10") Integer pageSize,
            @RequestParam(required = false) String pageToken,
            @RequestParam(defaultValue = "createTime desc") String orderBy) {
        return reportService.listReports(pageSize, pageToken, orderBy);
    }

    @Operation(summary = "生成优化报告（自定义方法，基于当前画像）")
    @PostMapping("/reports:generate")
    public ReportResponse generateReport() {
        return reportService.generateReport();
    }

    @Operation(summary = "Get - 获取报告详情")
    @GetMapping("/reports/{reportId}")
    public ReportResponse getReport(@PathVariable Long reportId) {
        return reportService.getReport(reportId);
    }

    @Operation(summary = "导出报告为 PDF（自定义方法）")
    @GetMapping("/reports/{reportId}:export")
    public ResponseEntity<byte[]> exportReport(@PathVariable Long reportId) {
        byte[] pdf = reportService.exportReport(reportId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"report-" + reportId + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }
}
