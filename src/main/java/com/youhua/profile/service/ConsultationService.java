package com.youhua.profile.service;

import com.youhua.profile.dto.request.CreateConsultationRequest;
import com.youhua.profile.dto.response.ConsultationResponse;

public interface ConsultationService {

    ConsultationResponse create(CreateConsultationRequest request);
}
