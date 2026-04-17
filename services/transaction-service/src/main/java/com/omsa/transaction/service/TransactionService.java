package com.omsa.transaction.service;

import com.omsa.transaction.dto.TransactionRequest;
import com.omsa.transaction.dto.TransactionResponse;
import com.omsa.transaction.dto.TransactionSummary;
import com.omsa.transaction.exception.InvalidTransactionStateException;
import com.omsa.transaction.exception.TransactionNotFoundException;
import com.omsa.transaction.model.Transaction;
import com.omsa.transaction.model.Transaction.TransactionStatus;
import com.omsa.transaction.repository.TransactionRepository;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final MeterRegistry meterRegistry;

    @Transactional
    public TransactionResponse initiateTransaction(TransactionRequest request) {
        log.info("Initiating {} transaction for account: {} region: {}",
                request.getType(), request.getAccountId(), request.getRegion());

        Timer.Sample sample = Timer.start(meterRegistry);

        Transaction txn = Transaction.builder()
                .referenceNumber(generateReference(request.getRegion()))
                .accountId(request.getAccountId())
                .counterpartyAccount(request.getCounterpartyAccount())
                .type(request.getType())
                .status(TransactionStatus.PENDING)
                .amount(request.getAmount())
                .currency(request.getCurrency() != null ? request.getCurrency() : "ZAR")
                .region(request.getRegion())
                .description(request.getDescription())
                .build();

        Transaction saved = transactionRepository.save(txn);

        sample.stop(meterRegistry.timer("transaction.initiate.duration",
                "type", request.getType().name(),
                "region", request.getRegion()));

        meterRegistry.counter("transactions.initiated.total",
                "type", request.getType().name(),
                "currency", txn.getCurrency(),
                "region", request.getRegion()).increment();

        log.info("Transaction created: {} [{}]", saved.getReferenceNumber(), saved.getId());
        return TransactionResponse.from(saved);
    }

    public TransactionResponse getById(UUID id) {
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));
        return TransactionResponse.from(txn);
    }

    public TransactionResponse getByReference(String reference) {
        Transaction txn = transactionRepository.findByReferenceNumber(reference)
                .orElseThrow(() -> new TransactionNotFoundException("Reference not found: " + reference));
        return TransactionResponse.from(txn);
    }

    public Page<TransactionResponse> getByAccount(String accountId, Pageable pageable) {
        return transactionRepository
                .findByAccountIdOrderByCreatedAtDesc(accountId, pageable)
                .map(TransactionResponse::from);
    }

    @Transactional
    public TransactionResponse updateStatus(UUID id, TransactionStatus newStatus, String reason) {
        Transaction txn = transactionRepository.findById(id)
                .orElseThrow(() -> new TransactionNotFoundException("Transaction not found: " + id));

        validateStatusTransition(txn.getStatus(), newStatus);

        TransactionStatus prev = txn.getStatus();
        txn.setStatus(newStatus);
        if (reason != null) {
            txn.setFailureReason(reason);
        }

        Transaction updated = transactionRepository.save(txn);

        meterRegistry.counter("transactions.status.changed",
                "from", prev.name(),
                "to", newStatus.name(),
                "region", txn.getRegion()).increment();

        log.info("Transaction {} status: {} -> {} (region: {})",
                txn.getReferenceNumber(), prev, newStatus, txn.getRegion());

        return TransactionResponse.from(updated);
    }

    public TransactionSummary getRegionSummary(String region, LocalDateTime from, LocalDateTime to) {
        return transactionRepository.getRegionSummary(region, from, to)
                .orElse(TransactionSummary.builder()
                        .region(region)
                        .totalCount(0L)
                        .completedCount(0L)
                        .failedCount(0L)
                        .build());
    }

    private void validateStatusTransition(TransactionStatus current, TransactionStatus next) {
        boolean terminal = current == TransactionStatus.COMPLETED
                || current == TransactionStatus.FAILED
                || current == TransactionStatus.REVERSED;
        if (terminal) {
            throw new InvalidTransactionStateException(
                    "Cannot transition from terminal state: " + current);
        }
    }

    private String generateReference(String region) {
        return "TXN-" + region.toUpperCase().replace("-", "") + "-"
                + System.currentTimeMillis() + "-"
                + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }
}
