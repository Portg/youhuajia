package com.youhua.profile.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.youhua.common.exception.BizException;
import com.youhua.common.exception.ErrorCode;
import com.youhua.common.util.RequestContextUtil;
import com.youhua.profile.dto.response.ListReportsResponse;
import com.youhua.profile.dto.response.ReportResponse;
import com.youhua.profile.entity.OptimizationReport;
import com.youhua.profile.mapper.OptimizationReportMapper;
import com.youhua.profile.service.ReportService;
import com.youhua.report.dto.ReportData;
import com.youhua.report.service.PdfExportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Base64;
import java.util.List;

/**
 * Adapter layer: delegates to report.service.ReportService (which does the heavy work)
 * and converts results to profile DTOs.
 *
 * <p>Bean name {@code profileReportService} avoids conflict with
 * {@link com.youhua.report.service.ReportService} which has no qualifier.
 */
@Slf4j
@Service("profileReportService")
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    /** Injected by field name to avoid bean name collision with this class. */
    private final com.youhua.report.service.ReportService reportGenerationService;
    private final PdfExportService pdfExportService;
    private final OptimizationReportMapper reportMapper;
    private final ObjectMapper objectMapper;

    @Override
    public ListReportsResponse listReports(Integer pageSize, String pageToken, String orderBy) {
        Long userId = RequestContextUtil.getCurrentUserId();

        int size = (pageSize != null && pageSize > 0) ? Math.min(pageSize, 100) : 10;

        LambdaQueryWrapper<OptimizationReport> query = new LambdaQueryWrapper<OptimizationReport>()
                .eq(OptimizationReport::getUserId, userId)
                .eq(OptimizationReport::getDeleted, 0)
                .orderByDesc(OptimizationReport::getCreateTime);

        // pageToken is Base64-encoded last seen id (cursor-based pagination)
        if (pageToken != null && !pageToken.isBlank()) {
            try {
                String decoded = new String(Base64.getDecoder().decode(pageToken));
                Long lastId = Long.parseLong(decoded);
                query.lt(OptimizationReport::getId, lastId);
            } catch (Exception e) {
                log.warn("[ReportService] Invalid pageToken={}, ignoring", pageToken);
            }
        }

        query.last("LIMIT " + (size + 1));

        List<OptimizationReport> reports = reportMapper.selectList(query);

        String nextPageToken = null;
        if (reports.size() > size) {
            reports = reports.subList(0, size);
            nextPageToken = Base64.getEncoder().encodeToString(
                    String.valueOf(reports.get(reports.size() - 1).getId()).getBytes());
        }

        List<ReportResponse> responseList = reports.stream()
                .map(this::toResponse)
                .toList();

        return ListReportsResponse.builder()
                .reports(responseList)
                .nextPageToken(nextPageToken)
                .build();
    }

    @Override
    public ReportResponse generateReport() {
        Long userId = RequestContextUtil.getCurrentUserId();
        Long reportId = reportGenerationService.generateReport(userId);

        OptimizationReport report = reportMapper.selectById(reportId);
        if (report == null) {
            log.error("[ReportService] Generated report not found: reportId={} userId={}", reportId, userId);
            throw new BizException(ErrorCode.REPORT_NOT_FOUND, "报告生成后未找到，请重试");
        }

        log.debug("[ReportService] generateReport completed: reportId={} userId={}", reportId, userId);
        return toResponse(report);
    }

    @Override
    public ReportResponse getReport(Long reportId) {
        Long userId = RequestContextUtil.getCurrentUserId();

        OptimizationReport report = reportMapper.selectById(reportId);
        if (report == null || !userId.equals(report.getUserId())) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }

        return toResponse(report);
    }

    @Override
    public byte[] exportReport(Long reportId) {
        Long userId = RequestContextUtil.getCurrentUserId();

        OptimizationReport report = reportMapper.selectById(reportId);
        if (report == null || !userId.equals(report.getUserId())) {
            throw new BizException(ErrorCode.REPORT_NOT_FOUND);
        }

        ReportData reportData;
        try {
            reportData = objectMapper.readValue(report.getProfileSnapshotJson(), ReportData.class);
        } catch (JsonProcessingException e) {
            log.error("[ReportService] Failed to deserialize reportData: reportId={}", reportId, e);
            throw new BizException(ErrorCode.REPORT_PDF_FAILED, "报告数据解析失败，无法导出PDF");
        }

        log.debug("[ReportService] exportReport: reportId={} userId={}", reportId, userId);
        return pdfExportService.export(reportData);
    }

    // ===================== Private helpers =====================

    private ReportResponse toResponse(OptimizationReport report) {
        return ReportResponse.builder()
                .name("reports/" + report.getId())
                .aiSummary(report.getAiSummary())
                .reportVersion(report.getReportVersion())
                .createTime(report.getCreateTime())
                .build();
    }
}
