package com.omsa.policy.repository;

import com.omsa.policy.model.Policy;
import com.omsa.policy.model.Policy.PolicyStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface PolicyRepository extends JpaRepository<Policy, UUID> {

    Optional<Policy> findByPolicyNumber(String policyNumber);

    Page<Policy> findByCustomerIdOrderByCreatedAtDesc(String customerId, Pageable pageable);

    Page<Policy> findByRegionAndStatusOrderByCreatedAtDesc(
            String region, PolicyStatus status, Pageable pageable);

    long countByCustomerIdAndStatus(String customerId, PolicyStatus status);
}
