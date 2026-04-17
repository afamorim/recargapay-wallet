package br.com.recargapay.wallet.shared.exception;

import java.util.UUID;

public class WalletNotFoundException extends WalletException {

    public WalletNotFoundException(UUID walletId) {
        super("Wallet not found: " + walletId);
    }
}
