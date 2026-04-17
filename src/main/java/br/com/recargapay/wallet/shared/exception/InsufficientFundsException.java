package br.com.recargapay.wallet.shared.exception;

import java.math.BigDecimal;
import java.util.UUID;

public class InsufficientFundsException extends WalletException {

    public InsufficientFundsException(UUID walletId, BigDecimal available, BigDecimal requested) {
        super("Insufficient funds in wallet %s: available %.4f, requested %.4f"
                .formatted(walletId, available, requested));
    }
}