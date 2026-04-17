package br.com.recargapay.wallet.shared.exception;

public abstract class WalletException extends RuntimeException {

    protected WalletException(String message) {
        super(message);
    }
}