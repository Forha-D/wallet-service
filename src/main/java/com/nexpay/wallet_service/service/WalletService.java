package com.nexpay.wallet_service.service;

import com.nexpay.wallet_service.dto.*;
import com.nexpay.wallet_service.exception.*;
import com.nexpay.wallet_service.model.*;
import com.nexpay.wallet_service.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Map;
//import java.util.Objects;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@SuppressWarnings("null")
public class WalletService {

    private final WalletRepository       walletRepository;
    private final TransactionRepository  transactionRepository;
    private final OutboxRepository       outboxRepository;

    // ─────────────────────────────────────────
    // CREATE WALLET
    // ─────────────────────────────────────────
    @Transactional
    public WalletResponse createWallet(UUID userId) {

        // prevent duplicate wallet per user
        if (walletRepository.existsByUserId(userId)) {
            throw new WalletAlreadyExistsException(userId);
        }

        Wallet wallet = Wallet.builder()
                .userId(userId)
                .balance(BigDecimal.ZERO)
                .currency("BDT")
                .status(WalletStatus.ACTIVE)
                .build();

       // Wallet saved = Objects.requireNonNull(walletRepository.save(wallet), "wallet save returned null");
            Wallet saved = walletRepository.save(wallet);

        log.info("wallet created for userId: {}", userId);

        return WalletResponse.from(saved);
    }

    // ─────────────────────────────────────────
    // GET BALANCE
    // ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public WalletResponse getBalance(UUID userId) {

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("userId", userId));

        return WalletResponse.from(wallet);
    }

    // ─────────────────────────────────────────
    // DEBIT
    // ─────────────────────────────────────────
    @Transactional
    public TransactionResponse debit(UUID userId, DebitRequest req) {

        // STEP 1: idempotency check
        if (transactionRepository.existsByIdempotencyKey(req.getIdempotencyKey())) {
            log.warn("duplicate debit detected for idempotency key: {}", req.getIdempotencyKey());
            return transactionRepository
                    .findByIdempotencyKey(req.getIdempotencyKey())
                    .map(TransactionResponse::from)
                    .orElseThrow(() -> new DuplicateTransactionException(req.getIdempotencyKey()));
        }

        // STEP 2: lock wallet row — prevent concurrent debits
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new WalletNotFoundException("userId", userId));

        // STEP 3: check wallet status
        if (wallet.getStatus() == WalletStatus.FROZEN) {
            throw new WalletFrozenException(userId);
        }

        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new WalletNotFoundException("wallet is closed for userId", userId);
        }

        // STEP 4: check balance
        if (wallet.getBalance().compareTo(req.getAmount()) < 0) {
            throw new InsufficientBalanceException(wallet.getBalance(), req.getAmount());
        }

        // STEP 5: snapshot balances
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter  = balanceBefore.subtract(req.getAmount());

        // STEP 6: update wallet balance
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);
        //Objects.requireNonNull(walletRepository.save(wallet), "wallet save returned null");

        // STEP 7: create transaction record
        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.DEBIT)
                .amount(req.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .idempotencyKey(req.getIdempotencyKey())
                .referenceId(req.getReferenceId())
                .referenceType(req.getReferenceType())
                .description(req.getDescription())
                .status(TransactionStatus.COMPLETED)
                .build();

        //WalletTransaction saved = Objects.requireNonNull(transactionRepository.save(tx), "transaction save returned null");
         WalletTransaction saved = transactionRepository.save(tx);

        // STEP 8: write outbox event — same transaction
        OutboxEvent outbox = OutboxEvent.builder()
                .eventType("wallet.debited")
                .payload(Map.of(
                        "wallet_id",      wallet.getId().toString(),
                        "user_id",        userId.toString(),
                        "amount",         req.getAmount().toString(),
                        "balance_before", balanceBefore.toString(),
                        "balance_after",  balanceAfter.toString(),
                        "reference_id",   req.getReferenceId().toString(),
                        "reference_type", req.getReferenceType(),
                        "transaction_id", saved.getId().toString()
                ))
                .status(OutboxStatus.PENDING)
                .walletId(wallet.getId())
                .build();

        //Objects.requireNonNull(outboxRepository.save(outbox), "outbox save returned null");
        outboxRepository.save(outbox);

        log.info("debit successful userId: {} amount: {} balanceAfter: {}",
                userId, req.getAmount(), balanceAfter);

        return TransactionResponse.from(saved);
    }

    // ─────────────────────────────────────────
    // CREDIT
    // ─────────────────────────────────────────
    @Transactional
    public TransactionResponse credit(UUID userId, CreditRequest req) {

        // STEP 1: idempotency check
        if (transactionRepository.existsByIdempotencyKey(req.getIdempotencyKey())) {
            log.warn("duplicate credit detected for idempotency key: {}", req.getIdempotencyKey());
            return transactionRepository
                    .findByIdempotencyKey(req.getIdempotencyKey())
                    .map(TransactionResponse::from)
                    .orElseThrow(() -> new DuplicateTransactionException(req.getIdempotencyKey()));
        }

        // STEP 2: lock wallet row
        Wallet wallet = walletRepository.findByUserIdWithLock(userId)
                .orElseThrow(() -> new WalletNotFoundException("userId", userId));

        // STEP 3: check wallet status
        if (wallet.getStatus() == WalletStatus.FROZEN) {
            throw new WalletFrozenException(userId);
        }

        if (wallet.getStatus() == WalletStatus.CLOSED) {
            throw new WalletNotFoundException("wallet is closed for userId", userId);
        }

        // STEP 4: snapshot balances
        BigDecimal balanceBefore = wallet.getBalance();
        BigDecimal balanceAfter  = balanceBefore.add(req.getAmount());

        // STEP 5: update wallet balance
        wallet.setBalance(balanceAfter);
        walletRepository.save(wallet);
        //Objects.requireNonNull(walletRepository.save(wallet), "wallet save returned null");

        // STEP 6: create transaction record
        WalletTransaction tx = WalletTransaction.builder()
                .wallet(wallet)
                .type(TransactionType.CREDIT)
                .amount(req.getAmount())
                .balanceBefore(balanceBefore)
                .balanceAfter(balanceAfter)
                .idempotencyKey(req.getIdempotencyKey())
                .referenceId(req.getReferenceId())
                .referenceType(req.getReferenceType())
                .description(req.getDescription())
                .status(TransactionStatus.COMPLETED)
                .build();

        //WalletTransaction saved = Objects.requireNonNull(transactionRepository.save(tx), "transaction save returned null");
        WalletTransaction saved = transactionRepository.save(tx);

        // STEP 7: write outbox event — same transaction
        OutboxEvent outbox = OutboxEvent.builder()
                .eventType("wallet.credited")
                .payload(Map.of(
                        "wallet_id",      wallet.getId().toString(),
                        "user_id",        userId.toString(),
                        "amount",         req.getAmount().toString(),
                        "balance_before", balanceBefore.toString(),
                        "balance_after",  balanceAfter.toString(),
                        "reference_id",   req.getReferenceId().toString(),
                        "reference_type", req.getReferenceType(),
                        "transaction_id", saved.getId().toString()
                ))
                .status(OutboxStatus.PENDING)
                .walletId(wallet.getId())
                .build();

        //Objects.requireNonNull(outboxRepository.save(outbox), "outbox save returned null");
        outboxRepository.save(outbox);

        log.info("credit successful userId: {} amount: {} balanceAfter: {}",
                userId, req.getAmount(), balanceAfter);

        return TransactionResponse.from(saved);
    }

    // ─────────────────────────────────────────
    // TRANSACTION HISTORY
    // ─────────────────────────────────────────
    @Transactional(readOnly = true)
    public PageResponse<TransactionResponse> getTransactionHistory(
            UUID userId,
            Pageable pageable) {

        Wallet wallet = walletRepository.findByUserId(userId)
                .orElseThrow(() -> new WalletNotFoundException("userId", userId));

        Page<WalletTransaction> page = transactionRepository
                .findByWalletIdOrderByCreatedAtDesc(wallet.getId(), pageable);

        return PageResponse.from(page, TransactionResponse::from);
    }

// ─────────────────────────────────────────
// WRITE DEBIT FAILED OUTBOX EVENT
// called when order debit fails
// ─────────────────────────────────────────
@Transactional
public void writeDebitFailedEvent(String orderId, String userId, String reason) {

    OutboxEvent outbox = OutboxEvent.builder()
            .eventType("wallet.debit.failed")
            .payload(Map.of(
                    "order_id", orderId,
                    "user_id",  userId,
                    "reason",   reason
            ))
            .status(OutboxStatus.PENDING)
            .walletId(UUID.fromString(userId))
            .build();

    outboxRepository.save(outbox);

    log.info("wallet.debit.failed outbox event written for orderId: {}", orderId);
}

}