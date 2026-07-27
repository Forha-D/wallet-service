package com.nexpay.wallet_service.repository;

import com.nexpay.wallet_service.model.OutboxEvent;
import com.nexpay.wallet_service.model.OutboxStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface OutboxRepository extends JpaRepository<OutboxEvent, UUID> {

    // fetch pending events for poller — uses partial index idx_outbox_status
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT o FROM OutboxEvent o
            WHERE o.status = 'PENDING'
            ORDER BY o.createdAt ASC
            """)
    List<OutboxEvent> findPendingEventsWithLock();

    // fetch pending events with limit — prevents poller from loading too many at once
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query(value = """
            SELECT * FROM wallet_outbox
            WHERE status = 'PENDING'
            ORDER BY created_at ASC
            LIMIT :limit
            """, nativeQuery = true)
    List<OutboxEvent> findPendingEventsWithLockAndLimit(@Param("limit") int limit);

    // mark as published after successful Kafka publish
    @Modifying
    @Query("""
            UPDATE OutboxEvent o
            SET o.status = 'PUBLISHED',
                o.publishedAt = :publishedAt
            WHERE o.id = :id
            """)
    void markAsPublished(
            @Param("id") UUID id,
            @Param("publishedAt") Instant publishedAt
    );

    // mark as failed after all retries exhausted
    @Modifying
    @Query("""
            UPDATE OutboxEvent o
            SET o.status = 'FAILED'
            WHERE o.id = :id
            """)
    void markAsFailed(@Param("id") UUID id);

    // find failed events — for monitoring and alerting
    List<OutboxEvent> findByStatusOrderByCreatedAtAsc(OutboxStatus status);

    // find by wallet ID — for debugging specific wallet events
    List<OutboxEvent> findByWalletIdOrderByCreatedAtDesc(UUID walletId);

    // count pending events — for health check and monitoring
    long countByStatus(OutboxStatus status);
}