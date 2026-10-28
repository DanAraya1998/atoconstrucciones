package com.daraya.atoconstrucciones.shared;

import java.math.BigDecimal;
import java.util.Currency;

/** Frontera de contrato: decimal exacto como string, sin reglas contables. */
public record Money(String amount, String currency) {
    public Money {
        if (amount == null || !amount.matches("-?(0|[1-9][0-9]{0,17})(\\.[0-9]{1,5})?")) {
            throw new IllegalArgumentException("Importe decimal inválido");
        }
        if (currency == null || !currency.matches("[A-Z]{3}")) throw new IllegalArgumentException("Moneda inválida");
        Currency.getInstance(currency);
    }
    public BigDecimal decimal() { return new BigDecimal(amount); }
}
