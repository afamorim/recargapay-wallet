package br.com.recargapay.wallet.wallet.dto.response;

import br.com.recargapay.wallet.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record WalletResponse(
        UUID id,
        UUID userId,
        BigDecimal balance,
        String currency,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt
) {
    public static WalletResponse from(Wallet wallet) {
        return new WalletResponse(
                wallet.getId(),
                wallet.getUserId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getCreatedAt(),
                wallet.getUpdatedAt());
    }
}