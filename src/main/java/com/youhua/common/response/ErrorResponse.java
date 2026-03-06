package com.youhua.common.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.util.List;

/**
 * Unified error response following google.rpc.Status pattern.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ErrorResponse {

    private ErrorDetail error;
    private String traceId;

    @Data
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class ErrorDetail {
        private int code;
        private String message;
        private String status;
        private List<Object> details;
    }
}
