package br.com.recargapay.wallet.transaction.repository;

import br.com.recargapay.wallet.transaction.entity.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    Page<Transaction> findByWalletIdOrderByCreatedAtDesc(UUID walletId, Pageable pageable);

    Optional<Transaction> findFirstByWalletIdAndCreatedAtLessThanEqualOrderByCreatedAtDesc(
            UUID walletId, OffsetDateTime at);
}