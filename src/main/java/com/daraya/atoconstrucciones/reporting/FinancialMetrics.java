package com.daraya.atoconstrucciones.reporting;
import java.math.BigDecimal;

/** Fórmulas RN-Fxx; bases futuras ausentes permanecen explícitamente ausentes. */
public final class FinancialMetrics {
    private FinancialMetrics() {}
    public record Result(BigDecimal contractBalance,BigDecimal collectionDue,BigDecimal customerCredit,BigDecimal projectCash,
        BigDecimal freeAfterKnownObligations,BigDecimal estimatedContractResult,BigDecimal commitmentCashReserve) {}
    public static Result calculate(BigDecimal contract,BigDecimal collections,BigDecimal receipts,BigDecimal payments,
        BigDecimal financing,BigDecimal transfersIn,BigDecimal transfersOut,BigDecimal otherOut,BigDecimal costs,BigDecimal due,BigDecimal uncoveredCommitments) {
        BigDecimal balance=contract.subtract(collections),cash=receipts.add(financing).add(transfersIn).subtract(payments).subtract(transfersOut).subtract(otherOut);
        return new Result(balance,balance.max(BigDecimal.ZERO),balance.negate().max(BigDecimal.ZERO),cash,cash.subtract(due).subtract(uncoveredCommitments),contract.subtract(costs),uncoveredCommitments);
    }
    public static BigDecimal commitmentReserve(BigDecimal remaining,BigDecimal unappliedAdvance) {return remaining.subtract(unappliedAdvance).max(BigDecimal.ZERO);}
}
