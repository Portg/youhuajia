package com.youhua.profile.service.impl;

import com.youhua.common.util.RequestContextUtil;
import com.youhua.profile.dto.request.CreateConsultationRequest;
import com.youhua.profile.dto.response.ConsultationResponse;
import com.youhua.profile.entity.ConsultationRequest;
import com.youhua.profile.mapper.ConsultationRequestMapper;
import com.youhua.profile.service.ConsultationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConsultationServiceImpl implements ConsultationService {

    private final ConsultationRequestMapper consultationRequestMapper;

    @Override
    public ConsultationResponse create(CreateConsultationRequest request) {
        Long userId = RequestContextUtil.getCurrentUserId();

        ConsultationRequest entity = new ConsultationRequest();
        entity.setUserId(userId);
        entity.setPhone(request.getPhone());
        entity.setConsultType(request.getConsultType());
        entity.setRemark(request.getRemark());
        entity.setStatus("PENDING");

        consultationRequestMapper.insert(entity);

        log.info("[ConsultationService] Created consultation: id={} userId={} type={}",
                entity.getId(), userId, request.getConsultType());

        return ConsultationResponse.builder()
                .name("consultations/" + entity.getId())
                .consultType(entity.getConsultType())
                .status(entity.getStatus())
                .createTime(entity.getCreateTime())
                .build();
    }
}
