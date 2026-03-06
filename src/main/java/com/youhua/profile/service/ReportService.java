package com.youhua.profile.service;

import com.youhua.profile.dto.response.ListReportsResponse;
import com.youhua.profile.dto.response.ReportResponse;

public interface ReportService {

    ListReportsResponse listReports(Integer pageSize, String pageToken, String orderBy);

    ReportResponse generateReport();

    ReportResponse getReport(Long reportId);

    byte[] exportReport(Long reportId);
}
