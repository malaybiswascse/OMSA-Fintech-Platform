package com.omsa.transaction.repository;

import com.omsa.transaction.dto.TransactionSummary;
import com.omsa.transaction.model.Transaction;
import com.omsa.transaction.model.Transaction.TransactionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Optional<Transaction> findByReferenceNumber(String referenceNumber);

    Page<Transaction> findByAccountIdOrderByCreatedAtDesc(String accountId, Pageable pageable);

    Page<Transaction> findByRegionAndStatusOrderByCreatedAtDesc(
            String region, TransactionStatus status, Pageable pageable);

    @Query("""
        SELECT new com.omsa.transaction.dto.TransactionSummary(
            t.region,
            COUNT(t),
            SUM(t.amount),
            SUM(CASE WHEN t.status = 'COMPLETED' THEN 1 ELSE 0 END),
            SUM(CASE WHEN t.status = 'FAILED'    THEN 1 ELSE 0 END),
            t.currency
        )
        FROM Transaction t
        WHERE t.region = :region
          AND t.createdAt BETWEEN :from AND :to
        GROUP BY t.region, t.currency
    """)
    TransactionSummary getRegionSummary(
            @Param("region")  String region,
            @Param("from")    LocalDateTime from,
            @Param("to")      LocalDateTime to);

    @Query("SELECT COUNT(t) FROM Transaction t WHERE t.status = :status AND t.region = :region")
    long countByStatusAndRegion(@Param("status") TransactionStatus status, @Param("region") String region);
}
