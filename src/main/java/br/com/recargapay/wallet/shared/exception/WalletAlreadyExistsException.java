package br.com.recargapay.wallet.shared.exception;

import java.util.UUID;

public class WalletAlreadyExistsException extends WalletException {

    public WalletAlreadyExistsException(UUID userId) {
        super("A wallet already exists for user: " + userId);
    }
}