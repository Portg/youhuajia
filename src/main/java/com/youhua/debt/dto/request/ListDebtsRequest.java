package com.youhua.debt.dto.request;

import lombok.Data;

@Data
public class ListDebtsRequest {

    private Integer pageSize = 20;
    private String pageToken;
    /** 过滤条件（AIP-160），如 status="CONFIRMED" */
    private String filter;
    /** 排序（AIP-132），如 apr desc */
    private String orderBy = "createTime desc";
}
