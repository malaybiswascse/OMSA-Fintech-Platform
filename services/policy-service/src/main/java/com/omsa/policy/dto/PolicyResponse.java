package com.omsa.policy.dto;

import com.omsa.policy.model.Policy;
import com.omsa.policy.model.Policy.PolicyStatus;
import com.omsa.policy.model.Policy.PolicyType;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyResponse {

    private UUID id;
    private String policyNumber;
    private String customerId;
    private PolicyType type;
    private PolicyStatus status;
    private BigDecimal premiumAmount;
    private BigDecimal sumAssured;
    private String currency;
    private String region;
    private LocalDate startDate;
    private LocalDate endDate;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static PolicyResponse from(Policy p) {
        return PolicyResponse.builder()
                .id(p.getId())
                .policyNumber(p.getPolicyNumber())
                .customerId(p.getCustomerId())
                .type(p.getType())
                .status(p.getStatus())
                .premiumAmount(p.getPremiumAmount())
                .sumAssured(p.getSumAssured())
                .currency(p.getCurrency())
                .region(p.getRegion())
                .startDate(p.getStartDate())
                .endDate(p.getEndDate())
                .createdAt(p.getCreatedAt())
                .updatedAt(p.getUpdatedAt())
                .build();
    }
}
