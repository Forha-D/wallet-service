package com.nexpay.wallet_service.exception;

import java.math.BigDecimal;

public class InsufficientBalanceException extends RuntimeException {

    public InsufficientBalanceException(BigDecimal balance, BigDecimal amount) {
        super("insufficient balance: available " + balance + " BDT, requested " + amount + " BDT");
    }
}