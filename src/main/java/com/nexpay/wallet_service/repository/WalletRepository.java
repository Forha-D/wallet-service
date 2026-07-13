package com.nexpay.wallet_service.repository;

import com.nexpay.wallet_service.model.Wallet;
import com.nexpay.wallet_service.model.WalletStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface WalletRepository extends JpaRepository<Wallet, UUID> {

    // find by user ID — standard lookup
    Optional<Wallet> findByUserId(UUID userId);

    // find by user ID with row lock — used during debit/credit
    // prevents double debit when two requests hit simultaneously
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.userId = :userId")
    Optional<Wallet> findByUserIdWithLock(@Param("userId") UUID userId);

    // find by wallet ID with row lock — used during debit/credit by wallet ID
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT w FROM Wallet w WHERE w.id = :id")
    Optional<Wallet> findByIdWithLock(@Param("id") UUID id);

    // check if wallet exists for user
    boolean existsByUserId(UUID userId);

    // find active wallet only
    Optional<Wallet> findByUserIdAndStatus(UUID userId, WalletStatus status);
}