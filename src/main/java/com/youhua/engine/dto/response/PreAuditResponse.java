package com.youhua.engine.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PreAuditResponse {

    private int probability;
    private List<String> suggestions;
}
