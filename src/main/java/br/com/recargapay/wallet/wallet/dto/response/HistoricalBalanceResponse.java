package br.com.recargapay.wallet.wallet.dto.response;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record HistoricalBalanceResponse(
        UUID walletId,
        BigDecimal balance,
        String currency,
        OffsetDateTime at
) {
    public static HistoricalBalanceResponse of(UUID walletId, BigDecimal balance, String currency, OffsetDateTime at) {
        return new HistoricalBalanceResponse(walletId, balance, currency, at);
    }
}