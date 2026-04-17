package br.com.recargapay.wallet.wallet.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateWalletRequest(

        @NotNull(message = "userId is required")
        UUID userId,

        @Size(min = 3, max = 3, message = "currency must be a 3-letter ISO 4217 code")
        @Pattern(regexp = "[A-Z]{3}", message = "currency must be a 3-letter uppercase ISO 4217 code")
        String currency
) {
    public CreateWalletRequest {
        if (currency == null || currency.isBlank()) {
            currency = "BRL";
        }
    }
}