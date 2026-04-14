package com.omsa.transaction.dto;

import com.omsa.transaction.model.Transaction;
import com.omsa.transaction.model.Transaction.*;
import jakarta.validation.constraints.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class TransactionRequest {
    @NotBlank  private String accountId;
    private String counterpartyAccount;
    @NotNull   private TransactionType type;
    @NotNull @DecimalMin("0.01") private BigDecimal amount;
    @Size(min=3, max=3) private String currency;
    @NotBlank  private String region;
    private String description;
}

class TransactionResponse {
    public UUID id;
    public String referenceNumber;
    public String accountId;
    public TransactionType type;
    public TransactionStatus status;
    public BigDecimal amount;
    public String currency;
    public String region;
    public LocalDateTime createdAt;
    public LocalDateTime updatedAt;

    public static TransactionResponse from(Transaction t) {
        TransactionResponse r = new TransactionResponse();
        r.id = t.getId(); r.referenceNumber = t.getReferenceNumber();
        r.accountId = t.getAccountId(); r.type = t.getType();
        r.status = t.getStatus(); r.amount = t.getAmount();
        r.currency = t.getCurrency(); r.region = t.getRegion();
        r.createdAt = t.getCreatedAt(); r.updatedAt = t.getUpdatedAt();
        return r;
    }
}

class TransactionSummary {
    public String region;
    public long totalCount;
    public BigDecimal totalVolume;
    public long completedCount;
    public long failedCount;
    public String currency;
}
