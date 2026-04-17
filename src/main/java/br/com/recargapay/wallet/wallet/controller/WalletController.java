package br.com.recargapay.wallet.wallet.controller;

import br.com.recargapay.wallet.wallet.dto.request.CreateWalletRequest;
import br.com.recargapay.wallet.wallet.dto.response.BalanceResponse;
import br.com.recargapay.wallet.wallet.dto.response.HistoricalBalanceResponse;
import br.com.recargapay.wallet.wallet.dto.response.WalletResponse;
import br.com.recargapay.wallet.wallet.service.WalletService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/wallets")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Wallets", description = "Wallet management")
public class WalletController {

    private final WalletService walletService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Create a new wallet for a user")
    public WalletResponse createWallet(@RequestBody @Valid CreateWalletRequest request) {
        log.debug("POST /api/v1/wallets - userId={}", request.userId());
        WalletResponse response = walletService.createWallet(request);
        log.debug("Wallet created - walletId={}", response.id());
        return response;
    }

    @GetMapping("/{walletId}")
    @Operation(summary = "Get wallet details")
    public WalletResponse getWallet(@PathVariable UUID walletId) {
        log.debug("GET /api/v1/wallets/{}", walletId);
        return walletService.getWallet(walletId);
    }

    @GetMapping("/{walletId}/balance")
    @Operation(summary = "Get the current balance of a wallet")
    public BalanceResponse getBalance(@PathVariable UUID walletId) {
        log.debug("GET /api/v1/wallets/{}/balance", walletId);
        return walletService.getBalance(walletId);
    }

    @GetMapping("/{walletId}/balance/history")
    @Operation(summary = "Get the wallet balance at a specific point in time")
    public HistoricalBalanceResponse getHistoricalBalance(
            @PathVariable UUID walletId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime at) {
        log.debug("GET /api/v1/wallets/{}/balance/history - at={}", walletId, at);
        return walletService.getHistoricalBalance(walletId, at);
    }
}