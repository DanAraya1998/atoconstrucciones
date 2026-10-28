package com.daraya.atoconstrucciones;
import com.daraya.atoconstrucciones.reporting.FinancialMetrics;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import static org.assertj.core.api.Assertions.*;
class FinancialMetricsTests {
    private BigDecimal n(String s){return new BigDecimal(s);}
    @Test void reproducesExampleAAndReservesOnlyUncoveredCommitments() {
        var a=FinancialMetrics.calculate(n("11000000"),n("4000000"),n("4000000"),n("2000000"),BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,n("2500000"),n("500000"),n("1500000"));
        assertThat(a.collectionDue()).isEqualByComparingTo("7000000");assertThat(a.projectCash()).isEqualByComparingTo("2000000");assertThat(a.freeAfterKnownObligations()).isZero();assertThat(a.estimatedContractResult()).isEqualByComparingTo("8500000");
        assertThat(FinancialMetrics.commitmentReserve(n("1000000"),n("400000"))).isEqualByComparingTo("600000");assertThat(FinancialMetrics.commitmentReserve(n("200000"),n("400000"))).isZero();
    }
}
