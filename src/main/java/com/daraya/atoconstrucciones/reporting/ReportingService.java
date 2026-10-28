package com.daraya.atoconstrucciones.reporting;

import com.daraya.atoconstrucciones.finance.FinancePolicy;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.shared.Money;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.*;

@Service
public class ReportingService {
    public record ClientSummary(UUID projectId,String currency,String basis,String formulaVersion,Instant asOf,boolean contractFormalized,
        Money initialContract,Money approvedExtraNet,Money currentContract,Money reportedDeposits,Money appliedCollections,
        Money contractBalance,Money collectionDue,Money customerCredit,Money unappliedCollections) {}
    public record Summary(UUID projectId,String currency,String basis,String formulaVersion,Instant asOf,boolean contractFormalized,
        Money initialContract,Money approvedExtraNet,Money currentContract,Money reportedDeposits,Money confirmedGrossReceipts,Money customerRefunds,
        Money confirmedCashReceipts,Money appliedCollections,Money contractBalance,Money collectionDue,Money customerCredit,Money unappliedCollections,
        Money registeredCosts,Money validatedCosts,Money grossCashPayments,Money supplierRefunds,Money cashPayments,Money appliedPayments,
        Money validatedDebtPending,Money supplierCredit,Money unappliedAdvances,Money financing,Money transfersIn,Money transfersOut,Money otherCashOut,
        Money availableProjectCash,Money freeAfterKnownObligations,Money estimatedContractResult,String budgetStatus,String commitmentStatus) {}
    public record Business(String currency,String basis,String formulaVersion,Instant asOf,Money currentContract,Money confirmedCashReceipts,
        Money validatedCosts,Money cashPayments,Money availableProjectCash,Money validatedDebtPending,Money collectionDue,Money customerCredit,Money supplierCredit) {}
    public record Followup(UUID projectId,String code,String name,Money collectionDue,Money customerCredit,Instant asOf) {}
    private final JdbcTemplate jdbc;private final FinancePolicy policy;private final ProjectService projects;
    public ReportingService(JdbcTemplate jdbc,FinancePolicy policy,ProjectService projects) {this.jdbc=jdbc;this.policy=policy;this.projects=projects;}
    // Una consulta agrupada sirve para obra y global; no reconstruye cada factura en Java.
    private static final String QUERY="""
        WITH cut AS (SELECT ?::timestamptz AS t,?::date AS d),
        bases AS (SELECT project_id,net_amount+tax_amount AS amount FROM contract,cut WHERE formalized_at<=cut.t),
        amendments AS (SELECT project_id,sum(net_delta+tax_delta) AS amount,sum(CASE WHEN kind IN ('EXTRA','CANCELLATION') THEN net_delta+tax_delta ELSE 0 END) AS extras FROM contract_amendment,cut WHERE effective_at<=cut.t GROUP BY project_id),
        reported AS (SELECT d.project_id,sum(d.money_amount) AS amount FROM deposit_report d,cut WHERE d.created_at<=cut.t AND d.operation_on<=cut.d AND
          (SELECT s.state FROM financial_state_event s WHERE s.aggregate_id=d.id AND s.aggregate_type='DEPOSIT' AND s.recorded_at<=cut.t ORDER BY s.recorded_at DESC,s.sequence DESC LIMIT 1) IN ('REPORTED','IN_REVIEW') GROUP BY d.project_id),
        pending AS (SELECT a.project_id,sum(a.gross_amount) AS amount FROM expense e JOIN expense_allocation a ON a.expense_id=e.id,cut WHERE e.created_at<=cut.t AND coalesce((SELECT (er.input->>'accruedOn')::date FROM expense_revision er WHERE er.expense_id=e.id AND er.recorded_at<=cut.t ORDER BY er.revision DESC LIMIT 1),e.accrued_on)<=cut.d AND a.allocation_revision=coalesce((SELECT er.revision FROM expense_revision er WHERE er.expense_id=e.id AND er.recorded_at<=cut.t ORDER BY er.revision DESC LIMIT 1),e.allocation_revision) AND
          (SELECT s.state FROM financial_state_event s WHERE s.aggregate_id=e.id AND s.aggregate_type='EXPENSE' AND s.recorded_at<=cut.t ORDER BY s.recorded_at DESC,s.sequence DESC LIMIT 1) IN ('REGISTERED','IN_REVIEW') GROUP BY a.project_id),
        cash_effect AS (SELECT movement_id,sum(amount) AS amount FROM treasury_effect,cut WHERE recorded_at<=cut.t AND effective_on<=cut.d GROUP BY movement_id),
        cash AS (SELECT m.project_id,
          sum(CASE WHEN m.kind='RECEIPT' THEN e.amount ELSE 0 END) AS receipts,
          sum(CASE WHEN m.kind='CUSTOMER_REFUND' THEN e.amount ELSE 0 END) AS customer_refunds,
          sum(CASE WHEN m.kind IN ('PAYMENT','ADVANCE') THEN e.amount ELSE 0 END) AS payments,
          sum(CASE WHEN m.kind='SUPPLIER_REFUND' THEN e.amount ELSE 0 END) AS supplier_refunds,
          sum(CASE WHEN m.kind='FINANCING' THEN e.amount ELSE 0 END) AS financing,
          sum(CASE WHEN m.kind='TRANSFER_IN' THEN e.amount ELSE 0 END) AS transfers_in,
          sum(CASE WHEN m.kind='TRANSFER_OUT' THEN e.amount ELSE 0 END) AS transfers_out,
          sum(CASE WHEN m.kind='OTHER_OUT' THEN e.amount ELSE 0 END) AS other_out
          FROM treasury_movement m JOIN cash_effect e ON e.movement_id=m.id GROUP BY m.project_id),
        costs AS (SELECT c.project_id,sum(c.amount) AS amount FROM cost_recognition c,cut WHERE c.recorded_at<=cut.t AND c.effective_on<=cut.d GROUP BY c.project_id),
        obligation_cost AS (SELECT coalesce(e.original_expense_id,e.id) AS expense_id,c.project_id,sum(c.amount) AS amount FROM expense e JOIN cost_recognition c ON c.expense_id=e.id,cut WHERE c.recorded_at<=cut.t AND c.effective_on<=cut.d GROUP BY coalesce(e.original_expense_id,e.id),c.project_id),
        application AS (SELECT expense_id,project_id,sum(amount) AS amount FROM payment_application,cut WHERE recorded_at<=cut.t GROUP BY expense_id,project_id),
        debt AS (SELECT c.project_id,sum(greatest(0,c.amount-coalesce(a.amount,0))) AS due,sum(greatest(0,coalesce(a.amount,0)-c.amount)) AS credit FROM obligation_cost c LEFT JOIN application a ON a.expense_id=c.expense_id AND a.project_id=c.project_id GROUP BY c.project_id),
        paid AS (SELECT project_id,sum(amount) AS amount FROM application GROUP BY project_id),
        collected AS (SELECT project_id,sum(amount) AS amount FROM collection_application,cut WHERE recorded_at<=cut.t GROUP BY project_id),
        advance_apps AS (SELECT movement_id,sum(amount) AS amount FROM payment_application,cut WHERE recorded_at<=cut.t GROUP BY movement_id),
        advance_refunds AS (SELECT m.source_movement_id,sum(e.amount) AS amount FROM treasury_movement m JOIN cash_effect e ON e.movement_id=m.id WHERE m.kind='SUPPLIER_REFUND' GROUP BY m.source_movement_id),
        advances AS (SELECT m.project_id,sum(greatest(0,e.amount-coalesce(a.amount,0)-coalesce(r.amount,0))) AS amount FROM treasury_movement m JOIN cash_effect e ON e.movement_id=m.id LEFT JOIN advance_apps a ON a.movement_id=m.id LEFT JOIN advance_refunds r ON r.source_movement_id=m.id WHERE m.kind='ADVANCE' GROUP BY m.project_id)
        SELECT p.id,coalesce(b.amount,0) AS initial,b.project_id IS NOT NULL AS formalized,coalesce(am.amount,0) AS amendments,coalesce(am.extras,0) AS extras,
          coalesce(r.amount,0) AS reported,coalesce(pn.amount,0) AS registered,coalesce(c.receipts,0) AS receipts,coalesce(c.customer_refunds,0) AS customer_refunds,
          coalesce(c.payments,0) AS payments,coalesce(c.supplier_refunds,0) AS supplier_refunds,coalesce(c.financing,0) AS financing,coalesce(c.transfers_in,0) AS transfers_in,coalesce(c.transfers_out,0) AS transfers_out,coalesce(c.other_out,0) AS other_out,
          coalesce(co.amount,0) AS costs,coalesce(d.due,0) AS due,coalesce(d.credit,0) AS credit,coalesce(pa.amount,0) AS paid,coalesce(cl.amount,0) AS collected,coalesce(ad.amount,0) AS advances
        FROM project p LEFT JOIN bases b ON b.project_id=p.id LEFT JOIN amendments am ON am.project_id=p.id LEFT JOIN reported r ON r.project_id=p.id LEFT JOIN pending pn ON pn.project_id=p.id LEFT JOIN cash c ON c.project_id=p.id LEFT JOIN costs co ON co.project_id=p.id LEFT JOIN debt d ON d.project_id=p.id LEFT JOIN paid pa ON pa.project_id=p.id LEFT JOIN collected cl ON cl.project_id=p.id LEFT JOIN advances ad ON ad.project_id=p.id
        WHERE p.created_at<=(SELECT t FROM cut) AND (?::uuid IS NULL OR p.id=?::uuid)
        """;
    private List<Summary> summaries(UUID project,Instant cut) {
        return jdbc.query(QUERY,(r,n)->{
            BigDecimal current=r.getBigDecimal("initial").add(r.getBigDecimal("amendments")),receipts=r.getBigDecimal("receipts").subtract(r.getBigDecimal("customer_refunds")),payments=r.getBigDecimal("payments").subtract(r.getBigDecimal("supplier_refunds")),collected=r.getBigDecimal("collected");
            var calc=FinancialMetrics.calculate(current,collected,receipts,payments,r.getBigDecimal("financing"),r.getBigDecimal("transfers_in"),r.getBigDecimal("transfers_out"),r.getBigDecimal("other_out"),r.getBigDecimal("costs"),r.getBigDecimal("due"),BigDecimal.ZERO);
            return new Summary(r.getObject("id",UUID.class),"CRC","GROSS","M2-GROSS-1",cut,r.getBoolean("formalized"),m(r.getBigDecimal("initial")),m(r.getBigDecimal("extras")),m(current),m(r.getBigDecimal("reported")),m(r.getBigDecimal("receipts")),m(r.getBigDecimal("customer_refunds")),m(receipts),m(collected),m(calc.contractBalance()),m(calc.collectionDue()),m(calc.customerCredit()),m(receipts.subtract(collected)),m(r.getBigDecimal("registered")),m(r.getBigDecimal("costs")),m(r.getBigDecimal("payments")),m(r.getBigDecimal("supplier_refunds")),m(payments),m(r.getBigDecimal("paid")),m(r.getBigDecimal("due")),m(r.getBigDecimal("credit")),m(r.getBigDecimal("advances")),m(r.getBigDecimal("financing")),m(r.getBigDecimal("transfers_in")),m(r.getBigDecimal("transfers_out")),m(r.getBigDecimal("other_out")),m(calc.projectCash()),m(calc.freeAfterKnownObligations()),m(calc.estimatedContractResult()),"NO_BUDGET_BASE","NOT_IMPLEMENTED_M5");
        },java.sql.Timestamp.from(cut),cut.atZone(FinancePolicy.ZONE).toLocalDate(),project,project);
    }
    private static Money m(BigDecimal value) {return FinancePolicy.money(value);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Summary project(Actor actor,UUID project,Instant asOf) {policy.operations(actor,project);projects.read(actor,project);var rows=summaries(project,FinancePolicy.cutoff(asOf));if(rows.isEmpty())throw com.daraya.atoconstrucciones.shared.ApiException.missing();return rows.getFirst();}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ClientSummary client(Actor actor,UUID project,Instant asOf) {
        policy.collections(actor,project);projects.read(actor,project);var rows=summaries(project,FinancePolicy.cutoff(asOf));if(rows.isEmpty())throw com.daraya.atoconstrucciones.shared.ApiException.missing();var s=rows.getFirst();
        return new ClientSummary(project,s.currency(),s.basis(),s.formulaVersion(),s.asOf(),s.contractFormalized(),s.initialContract(),s.approvedExtraNet(),s.currentContract(),s.reportedDeposits(),s.appliedCollections(),s.contractBalance(),s.collectionDue(),s.customerCredit(),s.unappliedCollections());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Business business(Actor actor,Instant asOf) {
        actor.requireManager();Instant cut=FinancePolicy.cutoff(asOf);var rows=summaries(null,cut);
        return new Business("CRC","GROSS","M2-GROSS-1",cut,sum(rows,Summary::currentContract),sum(rows,Summary::confirmedCashReceipts),sum(rows,Summary::validatedCosts),sum(rows,Summary::cashPayments),sum(rows,Summary::availableProjectCash),sum(rows,Summary::validatedDebtPending),sum(rows,Summary::collectionDue),sum(rows,Summary::customerCredit),sum(rows,Summary::supplierCredit));
    }
    private Money sum(List<Summary> rows,java.util.function.Function<Summary,Money> field) {return m(rows.stream().map(field).map(Money::decimal).reduce(BigDecimal.ZERO,BigDecimal::add));}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public List<Followup> followups(Actor actor,int page,int size) {
        var authorized=projects.list(actor,page,size).items();if(!actor.manager()&&!actor.roles().contains("ASISTENTE_ADMINISTRATIVA"))throw com.daraya.atoconstrucciones.shared.ApiException.forbidden();
        Instant cut=Instant.now();return authorized.stream().filter(p->actor.manager()||projects.capacity(actor,p.id(),"OPERATIONS")).map(p->{var s=summaries(p.id(),cut).getFirst();return new Followup(p.id(),p.code(),p.name(),s.collectionDue(),s.customerCredit(),cut);}).toList();
    }
}
