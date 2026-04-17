package br.com.recargapay.wallet.transaction.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.util.UUID;

public record TransferRequest(

        @NotNull(message = "sourceWalletId is required")
        UUID sourceWalletId,

        @NotNull(message = "destinationWalletId is required")
        UUID destinationWalletId,

        @NotNull(message = "amount is required")
        @DecimalMin(value = "0.01", message = "amount must be greater than zero")
        BigDecimal amount,

        String description
) {}
