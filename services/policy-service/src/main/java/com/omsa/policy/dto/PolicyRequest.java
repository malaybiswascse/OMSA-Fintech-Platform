package com.omsa.policy.dto;

import com.omsa.policy.model.Policy.PolicyType;
import jakarta.validation.constraints.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PolicyRequest {

    @NotBlank(message = "Customer ID is required")
    private String customerId;

    @NotNull(message = "Policy type is required")
    private PolicyType type;

    @NotNull(message = "Premium amount is required")
    @DecimalMin(value = "0.01", message = "Premium must be greater than 0")
    private BigDecimal premiumAmount;

    @NotNull(message = "Sum assured is required")
    @DecimalMin(value = "0.01", message = "Sum assured must be greater than 0")
    private BigDecimal sumAssured;

    @Size(min = 3, max = 3, message = "Currency must be a 3-letter ISO code")
    private String currency;

    @NotBlank(message = "Region is required")
    private String region;

    private LocalDate startDate;
    private LocalDate endDate;
}
