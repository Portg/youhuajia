package com.youhua.profile.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ConsultationResponse {

    private String name;
    private String consultType;
    private String status;
    private LocalDateTime createTime;
}
