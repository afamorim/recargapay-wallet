package br.com.recargapay.wallet.shared.exception;

public class LockAcquisitionException extends WalletException {

    public LockAcquisitionException(String lockKey) {
        super("Could not acquire lock for resource: " + lockKey);
    }
}
