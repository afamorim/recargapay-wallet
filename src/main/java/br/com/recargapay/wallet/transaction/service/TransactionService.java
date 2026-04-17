package br.com.recargapay.wallet.transaction.service;

import br.com.recargapay.wallet.shared.enums.TransactionType;
import br.com.recargapay.wallet.shared.exception.InsufficientFundsException;
import br.com.recargapay.wallet.shared.exception.InvalidRequestException;
import br.com.recargapay.wallet.shared.exception.WalletNotFoundException;
import br.com.recargapay.wallet.transaction.dto.request.DepositRequest;
import br.com.recargapay.wallet.transaction.dto.request.TransferRequest;
import br.com.recargapay.wallet.transaction.dto.request.WithdrawRequest;
import br.com.recargapay.wallet.transaction.dto.response.TransactionResponse;
import br.com.recargapay.wallet.transaction.dto.response.TransferResponse;
import br.com.recargapay.wallet.transaction.entity.Transaction;
import br.com.recargapay.wallet.transaction.repository.TransactionRepository;
import br.com.recargapay.wallet.transaction.component.ExchangeComponent;
import br.com.recargapay.wallet.transaction.component.WalletComponent;
import br.com.recargapay.wallet.wallet.entity.Wallet;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class TransactionService {

    private final WalletComponent walletComponent;
    private final TransactionRepository transactionRepository;
    private final ExchangeComponent exchangeComponent;

    public TransactionResponse deposit(UUID walletId, DepositRequest request) {
        log.info("Deposit started - walletId={}, amount={}", walletId, request.amount());
        return walletComponent.executeWithLock(walletId, () -> {
            Wallet wallet = walletComponent.findByIdWithLock(walletId);

            wallet.setBalance(wallet.getBalance().add(request.amount()));
            log.debug("Balance updated after deposit - walletId={}, newBalance={}", walletId, wallet.getBalance());

            Transaction transaction = transactionRepository.save(Transaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.DEPOSIT)
                    .amount(request.amount())
                    .balanceAfter(wallet.getBalance())
                    .description(request.description())
                    .build());

            walletComponent.evictFromCache(walletId);

            log.info("Deposit completed - walletId={}, txId={}, amount={}, balanceAfter={}",
                    walletId, transaction.getId(), request.amount(), wallet.getBalance());
            return TransactionResponse.from(transaction);
        });
    }

    public TransactionResponse withdraw(UUID walletId, WithdrawRequest request) {
        log.info("Withdrawal started - walletId={}, amount={}", walletId, request.amount());
        return walletComponent.executeWithLock(walletId, () -> {
            Wallet wallet = walletComponent.findByIdWithLock(walletId);

            if (wallet.getBalance().compareTo(request.amount()) < 0) {
                log.warn("Withdrawal rejected - insufficient funds - walletId={}, available={}, requested={}",
                        walletId, wallet.getBalance(), request.amount());
                throw new InsufficientFundsException(walletId, wallet.getBalance(), request.amount());
            }

            wallet.setBalance(wallet.getBalance().subtract(request.amount()));
            log.debug("Balance updated after withdrawal - walletId={}, newBalance={}", walletId, wallet.getBalance());

            Transaction transaction = transactionRepository.save(Transaction.builder()
                    .wallet(wallet)
                    .type(TransactionType.WITHDRAWAL)
                    .amount(request.amount())
                    .balanceAfter(wallet.getBalance())
                    .description(request.description())
                    .build());

            walletComponent.evictFromCache(walletId);

            log.info("Withdrawal completed - walletId={}, txId={}, amount={}, balanceAfter={}",
                    walletId, transaction.getId(), request.amount(), wallet.getBalance());
            return TransactionResponse.from(transaction);
        });
    }

    public TransferResponse transfer(TransferRequest request) {
        log.info("Transfer started - sourceWalletId={}, destinationWalletId={}, amount={}",
                request.sourceWalletId(), request.destinationWalletId(), request.amount());

        if (request.sourceWalletId().equals(request.destinationWalletId())) {
            log.warn("Transfer rejected - source and destination are the same wallet - walletId={}", request.sourceWalletId());
            throw new InvalidRequestException("Source and destination wallets must be different");
        }

        List<UUID> sortedIds = List.of(request.sourceWalletId(), request.destinationWalletId())
                .stream().sorted().toList();

        return walletComponent.executeWithLocks(sortedIds, () -> {
            List<Wallet> wallets = walletComponent.findAllByIdWithLock(sortedIds);

            Wallet source = wallets.stream()
                    .filter(w -> w.getId().equals(request.sourceWalletId()))
                    .findFirst()
                    .orElseThrow(() -> {
                        log.warn("Transfer rejected - source wallet not found - walletId={}", request.sourceWalletId());
                        return new WalletNotFoundException(request.sourceWalletId());
                    });

            Wallet destination = wallets.stream()
                    .filter(w -> w.getId().equals(request.destinationWalletId()))
                    .findFirst()
                    .orElseThrow(() -> {
                        log.warn("Transfer rejected - destination wallet not found - walletId={}", request.destinationWalletId());
                        return new WalletNotFoundException(request.destinationWalletId());
                    });

            if (source.getBalance().compareTo(request.amount()) < 0) {
                log.warn("Transfer rejected - insufficient funds - sourceWalletId={}, available={}, requested={}",
                        request.sourceWalletId(), source.getBalance(), request.amount());
                throw new InsufficientFundsException(
                        request.sourceWalletId(), source.getBalance(), request.amount());
            }

            BigDecimal creditAmount = request.amount();
            if (!source.getCurrency().equals(destination.getCurrency())) {
                log.info("Cross-currency transfer detected - sourceCurrency={}, destinationCurrency={}",
                        source.getCurrency(), destination.getCurrency());
                creditAmount = exchangeComponent.convert(request.amount(), source.getCurrency(), destination.getCurrency());
            }

            source.setBalance(source.getBalance().subtract(request.amount()));
            destination.setBalance(destination.getBalance().add(creditAmount));
            log.debug("Balances updated - sourceWalletId={}, sourceBalance={}, destinationWalletId={}, destinationBalance={}",
                    request.sourceWalletId(), source.getBalance(), request.destinationWalletId(), destination.getBalance());

            Transaction debit = transactionRepository.save(Transaction.builder()
                    .wallet(source)
                    .type(TransactionType.TRANSFER_OUT)
                    .amount(request.amount())
                    .balanceAfter(source.getBalance())
                    .description(request.description())
                    .build());

            Transaction credit = transactionRepository.save(Transaction.builder()
                    .wallet(destination)
                    .type(TransactionType.TRANSFER_IN)
                    .amount(creditAmount)
                    .balanceAfter(destination.getBalance())
                    .referenceId(debit.getId())
                    .description(request.description())
                    .build());

            debit.setReferenceId(credit.getId());

            walletComponent.evictFromCache(request.sourceWalletId());
            walletComponent.evictFromCache(request.destinationWalletId());

            log.info("Transfer completed - sourceWalletId={}, destinationWalletId={}, amount={}, debitTxId={}, creditTxId={}",
                    request.sourceWalletId(), request.destinationWalletId(), request.amount(), debit.getId(), credit.getId());
            return TransferResponse.of(debit, credit);
        });
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getTransactions(UUID walletId, Pageable pageable) {
        log.debug("Fetching transactions - walletId={}, page={}, size={}", walletId, pageable.getPageNumber(), pageable.getPageSize());
        walletComponent.findById(walletId);
        return transactionRepository
                .findByWalletIdOrderByCreatedAtDesc(walletId, pageable)
                .map(TransactionResponse::from);
    }
}
