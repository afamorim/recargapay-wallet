package br.com.recargapay.wallet.transaction.controller;

import br.com.recargapay.wallet.transaction.dto.request.DepositRequest;
import br.com.recargapay.wallet.transaction.dto.request.TransferRequest;
import br.com.recargapay.wallet.transaction.dto.request.WithdrawRequest;
import br.com.recargapay.wallet.transaction.dto.response.TransactionResponse;
import br.com.recargapay.wallet.transaction.dto.response.TransferResponse;
import br.com.recargapay.wallet.transaction.service.TransactionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Transactions", description = "Fund operations: deposit, withdraw, transfer")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/{walletId}/deposit")
    @Operation(summary = "Deposit funds into a wallet")
    public TransactionResponse deposit(
            @PathVariable UUID walletId,
            @RequestBody @Valid DepositRequest request) {
        log.debug("POST /api/v1/wallets/{}/deposit - amount={}", walletId, request.amount());
        TransactionResponse response = transactionService.deposit(walletId, request);
        log.debug("Deposit accepted - walletId={}, txId={}", walletId, response.id());
        return response;
    }

    @PostMapping("/{walletId}/withdraw")
    @Operation(summary = "Withdraw funds from a wallet")
    public TransactionResponse withdraw(
            @PathVariable UUID walletId,
            @RequestBody @Valid WithdrawRequest request) {
        log.debug("POST /api/v1/wallets/{}/withdraw - amount={}", walletId, request.amount());
        TransactionResponse response = transactionService.withdraw(walletId, request);
        log.debug("Withdrawal accepted - walletId={}, txId={}", walletId, response.id());
        return response;
    }

    @PostMapping("/transfer")
    @Operation(summary = "Transfer funds between two wallets")
    public TransferResponse transfer(@RequestBody @Valid TransferRequest request) {
        log.debug("POST /api/v1/wallets/transfer - source={}, destination={}, amount={}",
                request.sourceWalletId(), request.destinationWalletId(), request.amount());
        TransferResponse response = transactionService.transfer(request);
        log.debug("Transfer accepted - debitTxId={}, creditTxId={}",
                response.debit().id(), response.credit().id());
        return response;
    }

    @GetMapping("/{walletId}/transactions")
    @Operation(summary = "List transactions for a wallet (paginated, newest first)")
    public Page<TransactionResponse> getTransactions(
            @PathVariable UUID walletId,
            @PageableDefault(size = 20) Pageable pageable) {
        log.debug("GET /api/v1/wallets/{}/transactions - page={}, size={}", walletId, pageable.getPageNumber(), pageable.getPageSize());
        return transactionService.getTransactions(walletId, pageable);
    }
}
