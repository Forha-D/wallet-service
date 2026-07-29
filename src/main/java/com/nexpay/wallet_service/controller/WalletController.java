package com.nexpay.wallet_service.controller;

import com.nexpay.wallet_service.dto.*;
import com.nexpay.wallet_service.service.WalletService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/wallet")
@RequiredArgsConstructor
public class WalletController {

    private final WalletService walletService;

    // ─────────────────────────────────────────
    // CREATE WALLET
    // POST /wallet
    // ─────────────────────────────────────────
    @PostMapping
    public ResponseEntity<ApiResponse<WalletResponse>> createWallet(
            @RequestHeader("X-User-ID") String userId
    ) {
        log.info("create wallet request for userId: {}", userId);

        WalletResponse response = walletService.createWallet(UUID.fromString(userId));

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.success("wallet created successfully", response));
    }

    // ─────────────────────────────────────────
    // GET BALANCE
    // GET /wallet/balance
    // ─────────────────────────────────────────
    @GetMapping("/balance")
    public ResponseEntity<ApiResponse<WalletResponse>> getBalance(
            @RequestHeader("X-User-ID") String userId
    ) {
        log.info("get balance request for userId: {}", userId);

        WalletResponse response = walletService.getBalance(UUID.fromString(userId));

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    // ─────────────────────────────────────────
    // DEBIT
    // POST /wallet/debit
    // ─────────────────────────────────────────
    @PostMapping("/debit")
    public ResponseEntity<ApiResponse<TransactionResponse>> debit(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody DebitRequest req
    ) {
        log.info("debit request for userId: {} amount: {}", userId, req.getAmount());

        TransactionResponse response = walletService.debit(
                UUID.fromString(userId),
                req
        );

        return ResponseEntity.ok(ApiResponse.success("debit successful", response));
    }

    // ─────────────────────────────────────────
    // CREDIT
    // POST /wallet/credit
    // ─────────────────────────────────────────
    @PostMapping("/credit")
    public ResponseEntity<ApiResponse<TransactionResponse>> credit(
            @RequestHeader("X-User-ID") String userId,
            @Valid @RequestBody CreditRequest req
    ) {
        log.info("credit request for userId: {} amount: {}", userId, req.getAmount());

        TransactionResponse response = walletService.credit(
                UUID.fromString(userId),
                req
        );

        return ResponseEntity.ok(ApiResponse.success("credit successful", response));
    }

    // ─────────────────────────────────────────
    // TRANSACTION HISTORY
    // GET /wallet/transactions?page=0&size=20
    // ─────────────────────────────────────────
    @GetMapping("/transactions")
    public ResponseEntity<ApiResponse<PageResponse<TransactionResponse>>> getTransactions(
            @RequestHeader("X-User-ID") String userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        log.info("transaction history request for userId: {} page: {} size: {}",
                userId, page, size);

        // cap page size — never let client request too many rows
        int safeSize = Math.min(size, 100);

        Pageable pageable = PageRequest.of(page, safeSize);

        PageResponse<TransactionResponse> response = walletService
                .getTransactionHistory(UUID.fromString(userId), pageable);

        return ResponseEntity.ok(ApiResponse.success(response));
    }
}