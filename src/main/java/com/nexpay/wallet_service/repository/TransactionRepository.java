package com.nexpay.wallet_service.repository;

import com.nexpay.wallet_service.model.TransactionStatus;
import com.nexpay.wallet_service.model.TransactionType;
import com.nexpay.wallet_service.model.WalletTransaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<WalletTransaction, UUID> {

    // find by idempotency key — idempotency check before every debit/credit
    Optional<WalletTransaction> findByIdempotencyKey(String idempotencyKey);

    // paginated transaction history for a wallet — client facing API
    Page<WalletTransaction> findByWalletIdOrderByCreatedAtDesc(
            UUID walletId,
            Pageable pageable
    );

    // filter by type — DEBIT or CREDIT
    Page<WalletTransaction> findByWalletIdAndTypeOrderByCreatedAtDesc(
            UUID walletId,
            TransactionType type,
            Pageable pageable
    );

    // filter by status
    Page<WalletTransaction> findByWalletIdAndStatusOrderByCreatedAtDesc(
            UUID walletId,
            TransactionStatus status,
            Pageable pageable
    );

    // filter by date range — for audit and reporting
    @Query("""
            SELECT t FROM WalletTransaction t
            WHERE t.wallet.id = :walletId
            AND t.createdAt BETWEEN :from AND :to
            ORDER BY t.createdAt DESC
            """)
    Page<WalletTransaction> findByWalletIdAndDateRange(
            @Param("walletId") UUID walletId,
            @Param("from") Instant from,
            @Param("to") Instant to,
            Pageable pageable
    );

    // find by reference — useful for order payment lookup
    Optional<WalletTransaction> findByReferenceIdAndReferenceType(
            UUID referenceId,
            String referenceType
    );

    // check idempotency key exists — lightweight check
    boolean existsByIdempotencyKey(String idempotencyKey);
}