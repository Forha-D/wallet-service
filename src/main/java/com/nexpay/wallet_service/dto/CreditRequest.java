package com.nexpay.wallet_service.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data
public class CreditRequest {

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.0001", message = "amount must be greater than zero")
    private BigDecimal amount;

    @NotBlank(message = "idempotency key is required")
    private String idempotencyKey;

    @NotNull(message = "reference ID is required")
    private UUID referenceId;

    @NotBlank(message = "reference type is required")
    private String referenceType;

    private String description;
}
