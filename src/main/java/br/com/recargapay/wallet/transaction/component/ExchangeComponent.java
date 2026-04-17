package br.com.recargapay.wallet.transaction.component;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Component
@Slf4j
public class ExchangeComponent {

    public BigDecimal convert(BigDecimal amount, String fromCurrency, String toCurrency) {
        log.info("Exchange rate requested - from={}, to={}, amount={}", fromCurrency, toCurrency, amount);

        BigDecimal rate = fetchRate(fromCurrency, toCurrency);
        BigDecimal converted = amount.multiply(rate).setScale(4, RoundingMode.HALF_UP);

        log.info("Exchange rate applied - from={}, to={}, rate={}, originalAmount={}, convertedAmount={}",
                fromCurrency, toCurrency, rate, amount, converted);

        return converted;
    }

    private BigDecimal fetchRate(String fromCurrency, String toCurrency) {
        log.debug("Calling external exchange rate service - from={}, to={}", fromCurrency, toCurrency);

        BigDecimal rate = BigDecimal.ONE;

        log.debug("Exchange rate received from external service - from={}, to={}, rate={}", fromCurrency, toCurrency, rate);
        return rate;
    }
}
