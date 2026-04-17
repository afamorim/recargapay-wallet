package br.com.recargapay.wallet.wallet.service;

import br.com.recargapay.wallet.shared.config.CacheConfig;
import br.com.recargapay.wallet.shared.exception.InvalidRequestException;
import br.com.recargapay.wallet.shared.exception.WalletAlreadyExistsException;
import br.com.recargapay.wallet.shared.exception.WalletNotFoundException;
import br.com.recargapay.wallet.transaction.entity.Transaction;
import br.com.recargapay.wallet.transaction.repository.TransactionRepository;
import br.com.recargapay.wallet.wallet.dto.request.CreateWalletRequest;
import br.com.recargapay.wallet.wallet.dto.response.BalanceResponse;
import br.com.recargapay.wallet.wallet.dto.response.HistoricalBalanceResponse;
import br.com.recargapay.wallet.wallet.dto.response.WalletResponse;
import br.com.recargapay.wallet.wallet.entity.Wallet;
import br.com.recargapay.wallet.wallet.repository.WalletRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class WalletService {

    private final WalletRepository walletRepository;
    private final TransactionRepository transactionRepository;

    public WalletResponse createWallet(CreateWalletRequest request) {
        log.debug("Creating wallet - userId={}, currency={}", request.userId(), request.currency());

        if (walletRepository.existsByUserId(request.userId())) {
            log.warn("Wallet creation rejected - wallet already exists for userId={}", request.userId());
            throw new WalletAlreadyExistsException(request.userId());
        }

        Wallet wallet = Wallet.builder()
                .userId(request.userId())
                .balance(BigDecimal.ZERO)
                .currency(request.currency())
                .build();

        WalletResponse response = WalletResponse.from(save(wallet));
        log.info("Wallet created - walletId={}, userId={}, currency={}", response.id(), response.userId(), response.currency());
        return response;
    }

    @Transactional(readOnly = true)
    public WalletResponse getWallet(UUID walletId) {
        log.debug("Fetching wallet details - walletId={}", walletId);
        return WalletResponse.from(findOrThrow(walletId));
    }

    @Transactional(readOnly = true)
    public BalanceResponse getBalance(UUID walletId) {
        log.debug("Fetching balance - walletId={}", walletId);
        return BalanceResponse.from(findOrThrow(walletId));
    }

    @Transactional(readOnly = true)
    public HistoricalBalanceResponse getHistoricalBalance(UUID walletId, OffsetDateTime at) {
        log.debug("Fetching historical balance - walletId={}, at={}", walletId, at);
        Wallet wallet = findOrThrow(walletId);

        if (at.isBefore(wallet.getCreatedAt())) {
            log.warn("Historical balance rejected - requested timestamp {} is before wallet creation {} for walletId={}", at, wallet.getCreatedAt(), walletId);
            throw new InvalidRequestException(
                    "Requested timestamp %s is before the wallet was created at %s"
                            .formatted(at, wallet.getCreatedAt()));
        }

        BigDecimal balance = transactionRepository
                .findFirstByWalletIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(walletId, at)
                .map(Transaction::getBalanceAfter)
                .orElse(BigDecimal.ZERO);

        log.debug("Historical balance resolved - walletId={}, at={}, balance={}", walletId, at, balance);
        return HistoricalBalanceResponse.of(walletId, balance, wallet.getCurrency(), at);
    }

    @Cacheable(value = CacheConfig.WALLETS_CACHE, key = "#walletId")
    @Transactional(readOnly = true)
    public Wallet findById(UUID walletId) {
        log.debug("Cache miss - loading wallet from database - walletId={}", walletId);
        return findOrThrow(walletId);
    }

    public Wallet findByIdWithLock(UUID walletId) {
        log.debug("Loading wallet with lock - walletId={}", walletId);
        return findOrThrow(walletId);
    }

    public List<Wallet> findAllByIdWithLock(List<UUID> ids) {
        log.debug("Loading wallets with lock - ids={}", ids);
        return walletRepository.findAllById(ids);
    }

    @CachePut(value = CacheConfig.WALLETS_CACHE, key = "#result.id")
    public Wallet save(Wallet wallet) {
        log.debug("Saving wallet - walletId={}, balance={}", wallet.getId(), wallet.getBalance());
        return walletRepository.save(wallet);
    }

    @CacheEvict(value = CacheConfig.WALLETS_CACHE, key = "#walletId")
    public void evictFromCache(UUID walletId) {
        log.info("Cache evicted - walletId={}", walletId);
    }

    private Wallet findOrThrow(UUID walletId) {
        return walletRepository.findById(walletId)
                .orElseThrow(() -> {
                    log.warn("Wallet not found - walletId={}", walletId);
                    return new WalletNotFoundException(walletId);
                });
    }
}
