package br.com.recargapay.wallet.wallet.dto.response;

import br.com.recargapay.wallet.wallet.entity.Wallet;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record BalanceResponse(
        UUID walletId,
        BigDecimal balance,
        String currency,
        OffsetDateTime updatedAt
) {
    public static BalanceResponse from(Wallet wallet) {
        return new BalanceResponse(
                wallet.getId(),
                wallet.getBalance(),
                wallet.getCurrency(),
                wallet.getUpdatedAt());
    }
}