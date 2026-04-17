package br.com.recargapay.wallet;

import br.com.recargapay.wallet.shared.enums.TransactionType;
import br.com.recargapay.wallet.transaction.dto.request.DepositRequest;
import br.com.recargapay.wallet.transaction.dto.request.TransferRequest;
import br.com.recargapay.wallet.transaction.dto.request.WithdrawRequest;
import br.com.recargapay.wallet.transaction.dto.response.TransactionResponse;
import br.com.recargapay.wallet.transaction.dto.response.TransferResponse;
import br.com.recargapay.wallet.transaction.repository.TransactionRepository;
import br.com.recargapay.wallet.wallet.dto.request.CreateWalletRequest;
import br.com.recargapay.wallet.wallet.dto.response.BalanceResponse;
import br.com.recargapay.wallet.wallet.dto.response.HistoricalBalanceResponse;
import br.com.recargapay.wallet.wallet.dto.response.WalletResponse;
import br.com.recargapay.wallet.wallet.repository.WalletRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestRestTemplate
@Import(TestcontainersConfiguration.class)
class WalletControllerIT {

    private static final String BASE_URL = "/api/v1/wallets";

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private WalletRepository walletRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @BeforeEach
    void cleanDatabase() {
        transactionRepository.deleteAll();
        walletRepository.deleteAll();
    }

    @Test
    void createWallet_shouldReturn201_withWalletDetails() {
        UUID userId = UUID.randomUUID();
        CreateWalletRequest request = new CreateWalletRequest(userId, "BRL");

        ResponseEntity<WalletResponse> response = post(BASE_URL, request, WalletResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        WalletResponse body = response.getBody();
        assertThat(body).isNotNull();
        assertThat(body.id()).isNotNull();
        assertThat(body.userId()).isEqualTo(userId);
        assertThat(body.balance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(body.currency()).isEqualTo("BRL");
        assertThat(body.createdAt()).isNotNull();
    }

    @Test
    void createWallet_shouldDefaultToBrl_whenCurrencyIsNull() {
        CreateWalletRequest request = new CreateWalletRequest(UUID.randomUUID(), null);

        ResponseEntity<WalletResponse> response = post(BASE_URL, request, WalletResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody().currency()).isEqualTo("BRL");
    }

    @Test
    void createWallet_shouldReturn409_whenUserAlreadyHasWallet() {
        UUID userId = UUID.randomUUID();
        createWallet(userId);

        ResponseEntity<Map> response = post(BASE_URL, new CreateWalletRequest(userId, "BRL"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void createWallet_shouldReturn400_whenUserIdIsMissing() {
        ResponseEntity<Map> response = post(BASE_URL, new CreateWalletRequest(null, "BRL"), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getBalance_shouldReturnZero_forNewWallet() {
        UUID walletId = createWallet(UUID.randomUUID()).id();

        ResponseEntity<BalanceResponse> response =
                restTemplate.getForEntity(BASE_URL + "/{id}/balance", BalanceResponse.class, walletId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        BalanceResponse body = response.getBody();
        assertThat(body.walletId()).isEqualTo(walletId);
        assertThat(body.balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getBalance_shouldReturn404_forUnknownWallet() {
        ResponseEntity<Map> response =
                restTemplate.getForEntity(BASE_URL + "/{id}/balance", Map.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void deposit_shouldIncreaseBalance_andRecordTransaction() {
        UUID walletId = createWallet(UUID.randomUUID()).id();
        DepositRequest request = new DepositRequest(new BigDecimal("100.00"), "Initial deposit");

        ResponseEntity<TransactionResponse> response =
                post(BASE_URL + "/{id}/deposit", request, TransactionResponse.class, walletId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransactionResponse tx = response.getBody();
        assertThat(tx.walletId()).isEqualTo(walletId);
        assertThat(tx.type()).isEqualTo(TransactionType.DEPOSIT);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(tx.balanceAfter()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(tx.id()).isNotNull();
        assertThat(tx.createdAt()).isNotNull();

        assertBalance(walletId, new BigDecimal("100.00"));
    }

    @Test
    void deposit_shouldAccumulateBalanceAcrossMultipleDeposits() {
        UUID walletId = createWallet(UUID.randomUUID()).id();

        post(BASE_URL + "/{id}/deposit", new DepositRequest(new BigDecimal("50.00"), null),
                TransactionResponse.class, walletId);
        post(BASE_URL + "/{id}/deposit", new DepositRequest(new BigDecimal("75.50"), null),
                TransactionResponse.class, walletId);

        assertBalance(walletId, new BigDecimal("125.50"));
    }

    @Test
    void deposit_shouldReturn400_whenAmountIsZero() {
        UUID walletId = createWallet(UUID.randomUUID()).id();

        ResponseEntity<Map> response = post(BASE_URL + "/{id}/deposit",
                new DepositRequest(BigDecimal.ZERO, null), Map.class, walletId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void deposit_shouldReturn404_forUnknownWallet() {
        ResponseEntity<Map> response = post(BASE_URL + "/{id}/deposit",
                new DepositRequest(new BigDecimal("10.00"), null), Map.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void withdraw_shouldDecreaseBalance_andRecordTransaction() {
        UUID walletId = createWallet(UUID.randomUUID()).id();
        deposit(walletId, new BigDecimal("200.00"));

        ResponseEntity<TransactionResponse> response = post(BASE_URL + "/{id}/withdraw",
                new WithdrawRequest(new BigDecimal("80.00"), "Withdrawal"), TransactionResponse.class, walletId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransactionResponse tx = response.getBody();
        assertThat(tx.type()).isEqualTo(TransactionType.WITHDRAWAL);
        assertThat(tx.amount()).isEqualByComparingTo(new BigDecimal("80.00"));
        assertThat(tx.balanceAfter()).isEqualByComparingTo(new BigDecimal("120.00"));

        assertBalance(walletId, new BigDecimal("120.00"));
    }

    @Test
    void withdraw_shouldReturn422_whenBalanceIsInsufficient() {
        UUID walletId = createWallet(UUID.randomUUID()).id();
        deposit(walletId, new BigDecimal("50.00"));

        ResponseEntity<Map> response = post(BASE_URL + "/{id}/withdraw",
                new WithdrawRequest(new BigDecimal("100.00"), null), Map.class, walletId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void withdraw_shouldReturn422_whenWalletIsEmpty() {
        UUID walletId = createWallet(UUID.randomUUID()).id();

        ResponseEntity<Map> response = post(BASE_URL + "/{id}/withdraw",
                new WithdrawRequest(new BigDecimal("1.00"), null), Map.class, walletId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void transfer_shouldMoveFunds_andCreateLinkedTransactions() {
        UUID sourceId = createWallet(UUID.randomUUID()).id();
        UUID destinationId = createWallet(UUID.randomUUID()).id();
        deposit(sourceId, new BigDecimal("500.00"));

        TransferRequest request = new TransferRequest(sourceId, destinationId, new BigDecimal("200.00"), "Transfer");
        ResponseEntity<TransferResponse> response = post(BASE_URL + "/transfer", request, TransferResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransferResponse transfer = response.getBody();

        assertThat(transfer.debit().type()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(transfer.debit().walletId()).isEqualTo(sourceId);
        assertThat(transfer.debit().amount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(transfer.debit().balanceAfter()).isEqualByComparingTo(new BigDecimal("300.00"));

        assertThat(transfer.credit().type()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(transfer.credit().walletId()).isEqualTo(destinationId);
        assertThat(transfer.credit().amount()).isEqualByComparingTo(new BigDecimal("200.00"));
        assertThat(transfer.credit().balanceAfter()).isEqualByComparingTo(new BigDecimal("200.00"));

        assertThat(transfer.debit().referenceId()).isEqualTo(transfer.credit().id());
        assertThat(transfer.credit().referenceId()).isEqualTo(transfer.debit().id());

        assertBalance(sourceId, new BigDecimal("300.00"));
        assertBalance(destinationId, new BigDecimal("200.00"));
    }

    @Test
    void transfer_shouldReturn422_whenSourceHasInsufficientFunds() {
        UUID sourceId = createWallet(UUID.randomUUID()).id();
        UUID destinationId = createWallet(UUID.randomUUID()).id();
        deposit(sourceId, new BigDecimal("50.00"));

        ResponseEntity<Map> response = post(BASE_URL + "/transfer",
                new TransferRequest(sourceId, destinationId, new BigDecimal("100.00"), null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNPROCESSABLE_CONTENT);
    }

    @Test
    void transfer_shouldReturn400_whenSourceAndDestinationAreSameWallet() {
        UUID walletId = createWallet(UUID.randomUUID()).id();
        deposit(walletId, new BigDecimal("100.00"));

        ResponseEntity<Map> response = post(BASE_URL + "/transfer",
                new TransferRequest(walletId, walletId, new BigDecimal("10.00"), null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void transfer_shouldReturn404_whenSourceWalletDoesNotExist() {
        UUID destinationId = createWallet(UUID.randomUUID()).id();

        ResponseEntity<Map> response = post(BASE_URL + "/transfer",
                new TransferRequest(UUID.randomUUID(), destinationId, new BigDecimal("10.00"), null), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void transfer_shouldSucceed_whenWalletsHaveDifferentCurrencies_withOneToOneRate() {
        UUID sourceId = createWallet(UUID.randomUUID(), "BRL").id();
        UUID destinationId = createWallet(UUID.randomUUID(), "USD").id();
        deposit(sourceId, new BigDecimal("300.00"));

        TransferRequest request = new TransferRequest(sourceId, destinationId, new BigDecimal("100.00"), "Cross-currency transfer");
        ResponseEntity<TransferResponse> response = post(BASE_URL + "/transfer", request, TransferResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        TransferResponse transfer = response.getBody();

        assertThat(transfer.debit().type()).isEqualTo(TransactionType.TRANSFER_OUT);
        assertThat(transfer.debit().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(transfer.debit().balanceAfter()).isEqualByComparingTo(new BigDecimal("200.00"));

        assertThat(transfer.credit().type()).isEqualTo(TransactionType.TRANSFER_IN);
        assertThat(transfer.credit().amount()).isEqualByComparingTo(new BigDecimal("100.00"));
        assertThat(transfer.credit().balanceAfter()).isEqualByComparingTo(new BigDecimal("100.00"));

        assertBalance(sourceId, new BigDecimal("200.00"));
        assertBalance(destinationId, new BigDecimal("100.00"));
    }

    @Test
    void transfer_shouldPreserveBidirectionalLink_onCrossCurrencyTransfer() {
        UUID sourceId = createWallet(UUID.randomUUID(), "BRL").id();
        UUID destinationId = createWallet(UUID.randomUUID(), "USD").id();
        deposit(sourceId, new BigDecimal("500.00"));

        TransferRequest request = new TransferRequest(sourceId, destinationId, new BigDecimal("200.00"), null);
        TransferResponse transfer = post(BASE_URL + "/transfer", request, TransferResponse.class).getBody();

        assertThat(transfer.debit().referenceId()).isEqualTo(transfer.credit().id());
        assertThat(transfer.credit().referenceId()).isEqualTo(transfer.debit().id());
    }

    @Test
    void getHistoricalBalance_shouldReturnZero_beforeAnyTransactions() throws InterruptedException {
        WalletResponse wallet = createWallet(UUID.randomUUID());
        Thread.sleep(10);
        OffsetDateTime beforeDeposit = OffsetDateTime.now();
        Thread.sleep(10);

        deposit(wallet.id(), new BigDecimal("100.00"));

        ResponseEntity<HistoricalBalanceResponse> response = restTemplate.getForEntity(
                BASE_URL + "/{id}/balance/history?at={at}",
                HistoricalBalanceResponse.class,
                wallet.id(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(beforeDeposit));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().balance()).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void getHistoricalBalance_shouldReflectBalanceAtGivenTimestamp() throws InterruptedException {
        UUID walletId = createWallet(UUID.randomUUID()).id();

        deposit(walletId, new BigDecimal("100.00"));
        Thread.sleep(50);
        OffsetDateTime afterFirstDeposit = OffsetDateTime.now();
        Thread.sleep(10);
        deposit(walletId, new BigDecimal("200.00"));

        ResponseEntity<HistoricalBalanceResponse> response = restTemplate.getForEntity(
                BASE_URL + "/{id}/balance/history?at={at}",
                HistoricalBalanceResponse.class,
                walletId,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(afterFirstDeposit));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().balance()).isEqualByComparingTo(new BigDecimal("100.00"));

        assertBalance(walletId, new BigDecimal("300.00"));
    }

    @Test
    void getHistoricalBalance_shouldReturn400_whenTimestampIsBeforeWalletCreation() {
        UUID walletId = createWallet(UUID.randomUUID()).id();

        ResponseEntity<Map> response = restTemplate.getForEntity(
                BASE_URL + "/{id}/balance/history?at={at}",
                Map.class,
                walletId,
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now().minusDays(1)));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void getHistoricalBalance_shouldReturn404_forUnknownWallet() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                BASE_URL + "/{id}/balance/history?at={at}",
                Map.class,
                UUID.randomUUID(),
                DateTimeFormatter.ISO_OFFSET_DATE_TIME.format(OffsetDateTime.now()));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void getTransactions_shouldReturnPaginatedResults_newestFirst() {
        UUID walletId = createWallet(UUID.randomUUID()).id();
        deposit(walletId, new BigDecimal("50.00"));
        deposit(walletId, new BigDecimal("30.00"));
        post(BASE_URL + "/{id}/withdraw",
                new WithdrawRequest(new BigDecimal("20.00"), null), TransactionResponse.class, walletId);

        ResponseEntity<RestPageResponse<TransactionResponse>> response = restTemplate.exchange(
                BASE_URL + "/{id}/transactions?page=0&size=10",
                HttpMethod.GET,
                null,
                new ParameterizedTypeReference<>() {},
                walletId);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        RestPageResponse<TransactionResponse> page = response.getBody();
        assertThat(page.getTotalElements()).isEqualTo(3);
        assertThat(page.getContent()).hasSize(3);
        assertThat(page.getContent().getFirst().type()).isEqualTo(TransactionType.WITHDRAWAL);
    }

    @Test
    void getTransactions_shouldReturn404_forUnknownWallet() {
        ResponseEntity<Map> response = restTemplate.getForEntity(
                BASE_URL + "/{id}/transactions", Map.class, UUID.randomUUID());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private WalletResponse createWallet(UUID userId) {
        return createWallet(userId, "BRL");
    }

    private WalletResponse createWallet(UUID userId, String currency) {
        ResponseEntity<WalletResponse> response = post(
                BASE_URL, new CreateWalletRequest(userId, currency), WalletResponse.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return response.getBody();
    }

    private TransactionResponse deposit(UUID walletId, BigDecimal amount) {
        ResponseEntity<TransactionResponse> response = post(
                BASE_URL + "/{id}/deposit",
                new DepositRequest(amount, null),
                TransactionResponse.class,
                walletId);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private void assertBalance(UUID walletId, BigDecimal expected) {
        BalanceResponse balance = restTemplate
                .getForEntity(BASE_URL + "/{id}/balance", BalanceResponse.class, walletId)
                .getBody();
        assertThat(balance.balance()).isEqualByComparingTo(expected);
    }

    private <T> ResponseEntity<T> post(String url, Object body, Class<T> responseType, Object... uriVars) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), responseType, uriVars);
    }

    static class RestPageResponse<T> {
        private java.util.List<T> content;
        private long totalElements;
        private int totalPages;
        private int number;
        private int size;

        public java.util.List<T> getContent() { return content; }
        public void setContent(java.util.List<T> content) { this.content = content; }
        public long getTotalElements() { return totalElements; }
        public void setTotalElements(long totalElements) { this.totalElements = totalElements; }
        public int getTotalPages() { return totalPages; }
        public void setTotalPages(int totalPages) { this.totalPages = totalPages; }
        public int getNumber() { return number; }
        public void setNumber(int number) { this.number = number; }
        public int getSize() { return size; }
        public void setSize(int size) { this.size = size; }
    }
}
