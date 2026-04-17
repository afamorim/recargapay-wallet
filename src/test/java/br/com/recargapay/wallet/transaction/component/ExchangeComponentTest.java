package br.com.recargapay.wallet.transaction.component;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class ExchangeComponentTest {

    private ExchangeComponent exchangeComponent;

    @BeforeEach
    void setUp() {
        exchangeComponent = new ExchangeComponent();
    }


    @Test
    void convert_shouldReturnSameValue_whenRateIsOneToOne() {
        BigDecimal result = exchangeComponent.convert(new BigDecimal("100.00"), "BRL", "USD");

        assertThat(result).isEqualByComparingTo(new BigDecimal("100.00"));
    }

    @Test
    void convert_shouldReturnSameValue_forAnyGivenCurrencyPair() {
        BigDecimal amount = new BigDecimal("250.75");

        assertThat(exchangeComponent.convert(amount, "BRL", "USD")).isEqualByComparingTo(amount);
        assertThat(exchangeComponent.convert(amount, "USD", "EUR")).isEqualByComparingTo(amount);
        assertThat(exchangeComponent.convert(amount, "EUR", "GBP")).isEqualByComparingTo(amount);
        assertThat(exchangeComponent.convert(amount, "GBP", "JPY")).isEqualByComparingTo(amount);
    }

    @Test
    void convert_shouldReturnResultWithScaleOfFour() {
        BigDecimal result = exchangeComponent.convert(new BigDecimal("100"), "BRL", "USD");

        assertThat(result.scale()).isEqualTo(4);
    }

    @Test
    void convert_shouldPreserveValueWithHighPrecisionInput() {
        BigDecimal result = exchangeComponent.convert(new BigDecimal("99.9999"), "BRL", "USD");

        assertThat(result).isEqualByComparingTo(new BigDecimal("99.9999"));
        assertThat(result.scale()).isEqualTo(4);
    }

    @Test
    void convert_shouldHandleMinimumAllowedAmount() {
        BigDecimal result = exchangeComponent.convert(new BigDecimal("0.01"), "BRL", "USD");

        assertThat(result).isEqualByComparingTo(new BigDecimal("0.01"));
    }

    @Test
    void convert_shouldHandleLargeAmount() {
        BigDecimal result = exchangeComponent.convert(new BigDecimal("999999999.9999"), "USD", "EUR");

        assertThat(result).isEqualByComparingTo(new BigDecimal("999999999.9999"));
    }

    @Test
    void convert_shouldHandleSameCurrencyPair() {
        BigDecimal result = exchangeComponent.convert(new BigDecimal("500.00"), "BRL", "BRL");

        assertThat(result).isEqualByComparingTo(new BigDecimal("500.00"));
    }
}
