package br.com.recargapay.wallet.shared.exception;

public class InvalidRequestException extends WalletException {

    public InvalidRequestException(String message) {
        super(message);
    }
}