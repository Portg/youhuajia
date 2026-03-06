package com.youhua.profile.service;

import com.youhua.profile.dto.request.BatchCreateIncomesRequest;
import com.youhua.profile.dto.response.IncomeResponse;

import java.util.List;

public interface IncomeService {

    List<IncomeResponse> batchCreateIncomes(BatchCreateIncomesRequest request);
}
