package br.com.recargapay.wallet.shared.lock;

import br.com.recargapay.wallet.shared.exception.LockAcquisitionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Service
@RequiredArgsConstructor
@Slf4j
public class DistributedLockService {

    private static final String LOCK_PREFIX = "wallet:lock:";
    private static final Duration LOCK_TTL = Duration.ofSeconds(10);
    private static final Duration LOCK_WAIT = Duration.ofSeconds(5);
    private static final long RETRY_INTERVAL_MS = 50;

    private static final RedisScript<Long> RELEASE_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end",
            Long.class);

    private final StringRedisTemplate redisTemplate;

    public <T> T executeWithLock(UUID walletId, Supplier<T> action) {
        return executeWithLocks(List.of(walletId), action);
    }

    public <T> T executeWithLocks(List<UUID> orderedIds, Supplier<T> action) {
        List<String> keys = orderedIds.stream()
                .map(id -> LOCK_PREFIX + id)
                .toList();
        List<String> values = keys.stream()
                .map(ignored -> UUID.randomUUID().toString())
                .toList();

        acquireAll(keys, values);
        scheduleLockRelease(keys, values);

        try {
            return action.get();
        } catch (Exception e) {
            if (!TransactionSynchronizationManager.isActualTransactionActive()) {
                releaseAll(keys, values);
            }
            throw e;
        }
    }

    private void acquireAll(List<String> keys, List<String> values) {
        int acquired = 0;
        try {
            for (int i = 0; i < keys.size(); i++) {
                acquireLock(keys.get(i), values.get(i));
                acquired++;
            }
        } catch (Exception e) {
            for (int i = 0; i < acquired; i++) {
                releaseLock(keys.get(i), values.get(i));
            }
            throw e;
        }
    }

    private void acquireLock(String key, String value) {
        log.debug("Attempting to acquire lock - key={}", key);
        Instant deadline = Instant.now().plus(LOCK_WAIT);
        while (true) {
            Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, value, LOCK_TTL);
            if (Boolean.TRUE.equals(acquired)) {
                log.debug("Lock acquired - key={}", key);
                return;
            }
            if (Instant.now().isAfter(deadline)) {
                log.warn("Lock acquisition timed out after {}s - key={}", LOCK_WAIT.getSeconds(), key);
                throw new LockAcquisitionException(key);
            }
            log.debug("Lock busy, retrying in {}ms - key={}", RETRY_INTERVAL_MS, key);
            try {
                Thread.sleep(RETRY_INTERVAL_MS);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                log.warn("Lock acquisition interrupted - key={}", key);
                throw new LockAcquisitionException(key);
            }
        }
    }

    private void scheduleLockRelease(List<String> keys, List<String> values) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    releaseAll(keys, values);
                }
            });
        }
    }

    private void releaseLock(String key, String value) {
        Long result = redisTemplate.execute(RELEASE_SCRIPT, List.of(key), value);
        if (Boolean.TRUE.equals(result != null && result > 0)) {
            log.debug("Lock released - key={}", key);
        } else {
            log.warn("Lock release skipped - lock was already expired or taken by another thread - key={}", key);
        }
    }

    private void releaseAll(List<String> keys, List<String> values) {
        for (int i = 0; i < keys.size(); i++) {
            releaseLock(keys.get(i), values.get(i));
        }
    }
}
