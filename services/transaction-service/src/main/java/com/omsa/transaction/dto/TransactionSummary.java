package com.omsa.transaction.dto;

import lombok.*;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionSummary {

    private String region;
    private Long totalCount;
    private BigDecimal totalVolume;
    private Long completedCount;
    private Long failedCount;
    private String currency;
}
