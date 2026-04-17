package com.omsa.transaction.controller;

import com.omsa.transaction.dto.TransactionRequest;
import com.omsa.transaction.dto.TransactionResponse;
import com.omsa.transaction.dto.TransactionSummary;
import com.omsa.transaction.model.Transaction.TransactionStatus;
import com.omsa.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transactions", description = "Payment and transfer APIs for OMSA financial platform")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping
    @Operation(summary = "Initiate a new financial transaction")
    public ResponseEntity<TransactionResponse> initiateTransaction(
            @Valid @RequestBody TransactionRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(transactionService.initiateTransaction(request));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get transaction by UUID")
    public ResponseEntity<TransactionResponse> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(transactionService.getById(id));
    }

    @GetMapping("/reference/{reference}")
    @Operation(summary = "Get transaction by reference number")
    public ResponseEntity<TransactionResponse> getByReference(@PathVariable String reference) {
        return ResponseEntity.ok(transactionService.getByReference(reference));
    }

    @GetMapping("/account/{accountId}")
    @Operation(summary = "Get paginated transactions for an account")
    public ResponseEntity<Page<TransactionResponse>> getByAccount(
            @PathVariable String accountId,
            @RequestParam(defaultValue = "0")  int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(transactionService.getByAccount(
                accountId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Update transaction status")
    public ResponseEntity<TransactionResponse> updateStatus(
            @PathVariable UUID id,
            @RequestParam TransactionStatus status,
            @RequestParam(required = false) String reason) {
        return ResponseEntity.ok(transactionService.updateStatus(id, status, reason));
    }

    @GetMapping("/summary/region/{region}")
    @Operation(summary = "Get transaction summary for a region and time window")
    public ResponseEntity<TransactionSummary> getRegionSummary(
            @PathVariable String region,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to) {
        return ResponseEntity.ok(transactionService.getRegionSummary(region, from, to));
    }

    @GetMapping("/health")
    public ResponseEntity<String> health() {
        return ResponseEntity.ok("Transaction Service is UP");
    }
}
