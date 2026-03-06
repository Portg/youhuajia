package com.youhua.profile.dto.response;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ListReportsResponse {

    private List<ReportResponse> reports;
    private String nextPageToken;
}
