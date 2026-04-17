package com.omsa.policy.service;

import com.omsa.policy.dto.PolicyRequest;
import com.omsa.policy.dto.PolicyResponse;
import com.omsa.policy.exception.PolicyNotFoundException;
import com.omsa.policy.model.Policy;
import com.omsa.policy.model.Policy.PolicyStatus;
import com.omsa.policy.repository.PolicyRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PolicyService {

    private final PolicyRepository policyRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public PolicyResponse createPolicy(PolicyRequest request) {
        log.info("Creating {} policy for customer: {} region: {}",
                request.getType(), request.getCustomerId(), request.getRegion());

        Policy policy = Policy.builder()
                .policyNumber(generatePolicyNumber(request.getType().name(), request.getRegion()))
                .customerId(request.getCustomerId())
                .type(request.getType())
                .status(PolicyStatus.PENDING)
                .premiumAmount(request.getPremiumAmount())
                .sumAssured(request.getSumAssured())
                .currency(request.getCurrency() != null ? request.getCurrency() : "ZAR")
                .region(request.getRegion())
                .startDate(request.getStartDate() != null ? request.getStartDate() : LocalDate.now())
                .endDate(request.getEndDate())
                .build();

        Policy saved = policyRepository.save(policy);

        meterRegistry.counter("policies.created.total",
                "type", request.getType().name(),
                "region", request.getRegion()).increment();

        log.info("Policy created: {} [{}]", saved.getPolicyNumber(), saved.getId());
        return PolicyResponse.from(saved);
    }

    public PolicyResponse getById(UUID id) {
        Policy policy = policyRepository.findById(id)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + id));
        return PolicyResponse.from(policy);
    }

    public PolicyResponse getByPolicyNumber(String policyNumber) {
        Policy policy = policyRepository.findByPolicyNumber(policyNumber)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + policyNumber));
        return PolicyResponse.from(policy);
    }

    public Page<PolicyResponse> getByCustomer(String customerId, Pageable pageable) {
        return policyRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId, pageable)
                .map(PolicyResponse::from);
    }

    @Transactional
    public PolicyResponse updateStatus(UUID id, PolicyStatus newStatus) {
        Policy policy = policyRepository.findById(id)
                .orElseThrow(() -> new PolicyNotFoundException("Policy not found: " + id));

        PolicyStatus prev = policy.getStatus();
        policy.setStatus(newStatus);
        Policy updated = policyRepository.save(policy);

        meterRegistry.counter("policies.status.changed",
                "from", prev.name(),
                "to", newStatus.name(),
                "region", policy.getRegion()).increment();

        log.info("Policy {} status: {} -> {}", policy.getPolicyNumber(), prev, newStatus);
        return PolicyResponse.from(updated);
    }

    private String generatePolicyNumber(String type, String region) {
        return "POL-" + type.substring(0, 3).toUpperCase()
                + "-" + region.toUpperCase().replace("-", "").substring(0, Math.min(4, region.replace("-", "").length()))
                + "-" + System.currentTimeMillis()
                + "-" + UUID.randomUUID().toString().substring(0, 4).toUpperCase();
    }
}
