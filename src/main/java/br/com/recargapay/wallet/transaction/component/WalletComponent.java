package br.com.recargapay.wallet.transaction.component;

import br.com.recargapay.wallet.shared.lock.DistributedLockService;
import br.com.recargapay.wallet.wallet.entity.Wallet;
import br.com.recargapay.wallet.wallet.service.WalletService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Component
@RequiredArgsConstructor
public class WalletComponent {

    private final WalletService walletService;
    private final DistributedLockService lockService;

    public <T> T executeWithLock(UUID walletId, Supplier<T> action) {
        return lockService.executeWithLock(walletId, action);
    }

    public <T> T executeWithLocks(List<UUID> sortedIds, Supplier<T> action) {
        return lockService.executeWithLocks(sortedIds, action);
    }

    public Wallet findByIdWithLock(UUID walletId) {
        return walletService.findByIdWithLock(walletId);
    }

    public List<Wallet> findAllByIdWithLock(List<UUID> ids) {
        return walletService.findAllByIdWithLock(ids);
    }

    public Wallet findById(UUID walletId) {
        return walletService.findById(walletId);
    }

    public void evictFromCache(UUID walletId) {
        walletService.evictFromCache(walletId);
    }
}
