package com.omsa.policy.controller;

import com.omsa.policy.dto.PolicyRequest;
import com.omsa.policy.dto.PolicyResponse;
import com.omsa.policy.model.Policy.PolicyStatus;
import com.omsa.policy.service.PolicyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/policies")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Policies", description = "Insurance policy lifecycle management APIs")
public class PolicyController {

    private final PolicyService policyService;

    @PostMapping
    @Operation(summary = "Create a new insurance policy")
    public ResponseEntity<PolicyResponse> createPolicy(
            @Valid @RequestBody PolicyRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(policyService.createPolicy(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get policy by UUID")
    public ResponseEntity<PolicyResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(policyService.getById(id));
    }

    @GetMapping("/number/{policyNumber}")
    @Operation(summary = "Get policy by policy number")
    public ResponseEntity<PolicyResponse> getByPolicyNumber(@PathVariable String policyNumber) {
        return ResponseEntity.ok(policyService.getByPolicyNumber(policyNumber));
    }

    @GetMapping("/customer/{customerId}")
    @Operation(summary = "Get paginated policies for a customer")
    public ResponseEntity<Page<PolicyResponse>> getByCustomer(
            @PathVariable String customerId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(policyService.getByCustomer(
                customerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update policy status")
    public ResponseEntity<PolicyResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam PolicyStatus status) {
        return ResponseEntity.ok(policyService.updateStatus(id, status));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Policy Service is UP");
    }
}
