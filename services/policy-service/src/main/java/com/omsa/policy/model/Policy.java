package com.omsa.policy.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "policies", indexes = {
        @Index(name = "idx_policy_customer",  columnList = "customer_id"),
        @Index(name = "idx_policy_status",    columnList = "status"),
        @Index(name = "idx_policy_number",    columnList = "policy_number"),
        @Index(name = "idx_policy_region",    columnList = "region")
})
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Policy {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "policy_number", unique = true, nullable = false, length = 40)
    private String policyNumber;

    @Column(name = "customer_id", nullable = false, length = 30)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private PolicyType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PolicyStatus status;

    @Column(name = "premium_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal premiumAmount;

    @Column(name = "sum_assured", nullable = false, precision = 15, scale = 2)
    private BigDecimal sumAssured;

    @Column(name = "currency", length = 3, nullable = false)
    private String currency;

    @Column(name = "region", length = 20, nullable = false)
    private String region;

    @Column(name = "start_date")
    private LocalDate startDate;

    @Column(name = "end_date")
    private LocalDate endDate;

    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @Version
    private Long version;

    public enum PolicyType   { LIFE, HEALTH, VEHICLE, HOME, BUSINESS }
    public enum PolicyStatus { PENDING, ACTIVE, SUSPENDED, EXPIRED, CANCELLED }
}
