package br.com.recargapay.wallet.transaction.dto.response;

import br.com.recargapay.wallet.transaction.entity.Transaction;

public record TransferResponse(
        TransactionResponse debit,
        TransactionResponse credit
) {
    public static TransferResponse of(Transaction debit, Transaction credit) {
        return new TransferResponse(
                TransactionResponse.from(debit),
                TransactionResponse.from(credit));
    }
}
