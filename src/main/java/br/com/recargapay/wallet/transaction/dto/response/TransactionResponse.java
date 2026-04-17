package br.com.recargapay.wallet.transaction.dto.response;

import br.com.recargapay.wallet.shared.enums.TransactionType;
import br.com.recargapay.wallet.transaction.entity.Transaction;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public record TransactionResponse(
        UUID id,
        UUID walletId,
        TransactionType type,
        BigDecimal amount,
        BigDecimal balanceAfter,
        UUID referenceId,
        String description,
        OffsetDateTime createdAt
) {
    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getWallet().getId(),
                transaction.getType(),
                transaction.getAmount(),
                transaction.getBalanceAfter(),
                transaction.getReferenceId(),
                transaction.getDescription(),
                transaction.getCreatedAt());
    }
}
