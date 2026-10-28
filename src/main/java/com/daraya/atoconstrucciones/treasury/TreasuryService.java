package com.daraya.atoconstrucciones.treasury;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.costs.CostService;
import com.daraya.atoconstrucciones.finance.FinancePolicy;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class TreasuryService {
    public record AccountInput(@NotBlank @Size(max=120) String name,@NotNull @Pattern(regexp="BANK|CASH") String kind) {}
    public record Account(UUID id,String name,String kind,String currency) {}
    public record MovementInput(@NotNull UUID projectId,@NotNull UUID accountId,
        @NotNull @Pattern(regexp="RECEIPT|PAYMENT|ADVANCE|SUPPLIER_REFUND|CUSTOMER_REFUND|FINANCING|OTHER_OUT") String kind,
        UUID supplierId,@NotNull Money money,@NotNull LocalDate operationDate,@NotNull @Pattern(regexp="BANK_TRANSFER|CASH|CARD|OTHER") String paymentMethod,
        @NotNull @Size(max=160) String bankReference,@NotBlank @Size(max=160) String factKey,UUID sourceMovementId,
        UUID evidenceDocumentId,@NotNull @Size(max=1000) String exceptionReason) {}
    public record Decision(UUID evidenceDocumentId,@NotBlank @Size(max=1000) String reason) {}
    public record Application(@NotNull UUID expenseId,@NotNull Money money,@NotBlank @Size(max=1000) String reason) {}
    public record CollectionInput(@NotNull Money money,@NotBlank @Size(max=1000) String reason) {}
    public record Reverse(@NotNull Money money,@NotBlank @Size(max=1000) String reason) {}
    public record TransferInput(@NotNull UUID fromProjectId,@NotNull UUID toProjectId,@NotNull UUID fromAccountId,@NotNull UUID toAccountId,
        @NotNull Money money,@NotNull LocalDate operationDate,@NotBlank @Size(max=160) String factKey,UUID evidenceDocumentId,@NotBlank @Size(max=1000) String reason) {}
    public record Movement(UUID id,UUID projectId,UUID accountId,String kind,UUID supplierId,Money money,Money remainingAmount,
        LocalDate operationDate,String paymentMethod,String bankReference,String factKey,UUID sourceMovementId,UUID transferId,
        String state,long version,Instant confirmedAt,Money appliedAmount,Money availableToApply) {}
    public record DepositInput(@NotNull Money money,@NotNull LocalDate operationDate,
        @NotNull @Pattern(regexp="BANK_TRANSFER|CASH|CARD|OTHER") String paymentMethod,@NotNull @Size(max=160) String bankReference,
        @NotNull UUID evidenceDocumentId,@NotNull @Size(max=1000) String note) {}
    public record Deposit(UUID id,UUID projectId,Money money,LocalDate operationDate,String paymentMethod,String bankReference,
        UUID evidenceDocumentId,String note,String state,UUID movementId,long version,String decisionReason,boolean referenceCoincidence) {}
    public record ConfirmDeposit(UUID existingMovementId,UUID accountId,@Size(max=160) String factKey,UUID evidenceDocumentId,
        @NotNull Money appliedMoney,@NotBlank @Size(max=1000) String reason) {}
    private final JdbcTemplate jdbc; private final FinancePolicy policy; private final CostService costs; private final IdempotencyService commands; private final AuditService audit;
    public TreasuryService(JdbcTemplate jdbc,FinancePolicy policy,CostService costs,IdempotencyService commands,AuditService audit) {this.jdbc=jdbc;this.policy=policy;this.costs=costs;this.commands=commands;this.audit=audit;}
    public List<Account> accounts(Actor actor) {actor.requireManager();return jdbc.query("SELECT * FROM treasury_account ORDER BY name",(r,n)->new Account(r.getObject("id",UUID.class),r.getString("name"),r.getString("kind"),r.getString("currency")));}
    public List<Account> projectAccounts(Actor actor,UUID project) {policy.operations(actor,project);return jdbc.query("SELECT * FROM treasury_account ORDER BY name",(r,n)->new Account(r.getObject("id",UUID.class),r.getString("name"),r.getString("kind"),r.getString("currency")));}
    @Transactional public Account account(Actor actor,String key,AccountInput input) {
        policy.approve(actor);return commands.execute(actor.id(),"POST /treasury/accounts",key,input,Account.class,()->{UUID id=UUID.randomUUID();jdbc.update("INSERT INTO treasury_account(id,name,kind,created_by) VALUES (?,?,?,?)",id,input.name(),input.kind(),actor.id());audit.record(actor.id(),"TREASURY_ACCOUNT_CREATED",id);return new Account(id,input.name(),input.kind(),"CRC");});
    }
    public UUID projectOf(UUID movement) {var ids=jdbc.queryForList("SELECT project_id FROM treasury_movement WHERE id=?",UUID.class,movement);if(ids.isEmpty())throw ApiException.missing();return ids.getFirst();}
    public Movement load(UUID id,boolean lock) {
        var rows=jdbc.query("SELECT * FROM treasury_movement WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->{
            BigDecimal remaining=remaining(id),applied=applied(id);return new Movement(id,r.getObject("project_id",UUID.class),r.getObject("account_id",UUID.class),r.getString("kind"),r.getObject("supplier_id",UUID.class),FinancePolicy.money(r.getBigDecimal("money_amount")),FinancePolicy.money(remaining),r.getObject("operation_on",LocalDate.class),r.getString("payment_method"),r.getString("bank_reference"),r.getString("fact_key"),r.getObject("source_movement_id",UUID.class),r.getObject("transfer_id",UUID.class),r.getString("state"),r.getLong("version"),r.getTimestamp("confirmed_at")==null?null:r.getTimestamp("confirmed_at").toInstant(),FinancePolicy.money(applied),FinancePolicy.money(remaining.subtract(applied).max(BigDecimal.ZERO)));
        },id);if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    public Movement read(Actor actor,UUID id) {policy.operations(actor,projectOf(id));return load(id,false);}
    public List<Movement> list(Actor actor,UUID project,int page,int size) {policy.operations(actor,project);CostService.pagination(page,size);return jdbc.queryForList("SELECT id FROM treasury_movement WHERE project_id=? ORDER BY created_at,id LIMIT ? OFFSET ?",UUID.class,project,size,page*size).stream().map(id->load(id,false)).toList();}
    public BigDecimal remaining(UUID id) {return jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM treasury_effect WHERE movement_id=?",BigDecimal.class,id).subtract(jdbc.queryForObject("SELECT coalesce(sum(e.amount),0) FROM treasury_effect e JOIN treasury_movement m ON m.id=e.movement_id WHERE m.source_movement_id=?",BigDecimal.class,id));}
    public BigDecimal applied(UUID id) {return jdbc.queryForObject("SELECT (SELECT coalesce(sum(amount),0) FROM payment_application WHERE movement_id=?)+(SELECT coalesce(sum(amount),0) FROM collection_application WHERE movement_id=?)",BigDecimal.class,id,id);}
    private void movementInput(MovementInput i) {
        FinancePolicy.amount(i.money(),2,true);FinancePolicy.date(i.operationDate());
        if(i.factKey()==null||i.factKey().isBlank()||i.factKey().length()>160)Versions.invalid("Identifique el hecho de caja.");
        boolean supplier=Set.of("PAYMENT","ADVANCE","SUPPLIER_REFUND").contains(i.kind());if(supplier!=(i.supplierId()!=null))Versions.invalid("El proveedor es obligatorio únicamente para pagos y anticipos.");
        boolean refund=Set.of("SUPPLIER_REFUND","CUSTOMER_REFUND").contains(i.kind());if(refund!=(i.sourceMovementId()!=null))Versions.invalid("La devolución requiere su movimiento original.");
        var accounts=jdbc.queryForList("SELECT kind FROM treasury_account WHERE id=?",String.class,i.accountId());if(accounts.isEmpty())throw ApiException.missing();
        if(accounts.getFirst().equals("CASH")!=i.paymentMethod().equals("CASH"))Versions.invalid("El medio efectivo requiere una cuenta de caja.");
        if(supplier&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM party WHERE id=? AND kind='SUPPLIER' AND active)",Boolean.class,i.supplierId())))throw ApiException.missing();
    }
    private UUID insert(Actor actor,MovementInput i) {
        UUID id=UUID.randomUUID();jdbc.update("INSERT INTO treasury_movement(id,project_id,account_id,kind,supplier_id,money_amount,currency,operation_on,payment_method,bank_reference,fact_key,source_movement_id,evidence_id,exception_reason,created_by) VALUES (?,?,?,?,?,?,'CRC',?,?,?,?,?,?,?,?)",id,i.projectId(),i.accountId(),i.kind(),i.supplierId(),i.money().decimal(),i.operationDate(),i.paymentMethod(),i.bankReference(),i.factKey(),i.sourceMovementId(),i.evidenceDocumentId(),i.exceptionReason(),actor.id());return id;
    }
    @Transactional public Movement create(Actor actor,String key,MovementInput input) {
        policy.operations(actor,input.projectId());movementInput(input);
        if(input.sourceMovementId()!=null&&!projectOf(input.sourceMovementId()).equals(input.projectId()))throw ApiException.missing();
        return commands.execute(actor.id(),"POST /treasury/movements",key,input,Movement.class,()->{
            policy.lock(actor,List.of(input.projectId()),false);UUID id=insert(actor,input);audit.record(actor.id(),"MOVEMENT_PREPARED",input.projectId(),Map.of("movementId",id));return load(id,false);
        });
    }
    private void confirmFact(Actor actor,Movement m,UUID evidence,String reason) {
        if(!m.state().equals("DRAFT"))Versions.conflict("El movimiento ya fue confirmado.");
        FinancePolicy.date(m.operationDate());
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"treasury-fact:"+m.accountId()+":"+m.factKey());
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM treasury_movement WHERE account_id=? AND fact_key=? AND confirmed_at IS NOT NULL)",Boolean.class,m.accountId(),m.factKey())))throw new ApiException(409,"DUPLICATE_CASH_FACT","El hecho ya está confirmado; vincule el movimiento existente.");
        UUID retained=evidence!=null?evidence:(UUID)jdbc.queryForMap("SELECT evidence_id FROM treasury_movement WHERE id=?",m.id()).get("evidence_id");
        policy.evidence(actor,m.projectId(),retained,reason,"CASH_"+m.id());
        if(m.sourceMovementId()!=null) {
            Movement source=load(m.sourceMovementId(),true);
            if(!source.projectId().equals(m.projectId())||!Objects.equals(source.supplierId(),m.supplierId())||
                !(m.kind().equals("CUSTOMER_REFUND")?source.kind().equals("RECEIPT"):Set.of("PAYMENT","ADVANCE").contains(source.kind()))||source.state().equals("DRAFT"))Versions.invalid("La devolución no corresponde al hecho original.");
            if(remaining(source.id()).compareTo(m.money().decimal())<0)Versions.invalid("La devolución excede el saldo original.");
            reduceApplications(actor,source,m.money().decimal(),reason);
        }
        jdbc.update("UPDATE treasury_movement SET state='CONFIRMED',confirmed_at=clock_timestamp(),version=version+1 WHERE id=?",m.id());
        jdbc.update("INSERT INTO treasury_effect(id,movement_id,amount,effective_on,actor_id,reason) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),m.id(),m.money().decimal(),m.operationDate(),actor.id(),reason);
        audit.record(actor.id(),"MOVEMENT_CONFIRMED",m.projectId(),Map.of("movementId",m.id(),"reason",reason));
    }
    @Transactional public Movement confirm(Actor actor,UUID id,String key,String etag,Decision input) {
        policy.approve(actor);UUID project=projectOf(id);policy.operations(actor,project);FinancePolicy.reason(input.reason());
        return commands.execute(actor.id(),"POST /treasury/movements/"+id+"/confirm",key,List.of(input,Objects.toString(etag,"")),Movement.class,()->{
            policy.lock(actor,List.of(project),false);var m=load(id,true);Versions.check(etag,m.version());confirmFact(actor,m,input.evidenceDocumentId(),input.reason());return load(id,false);
        });
    }
    @Transactional public Movement apply(Actor actor,UUID id,String key,String etag,Application input) {
        policy.approve(actor);UUID project=projectOf(id);policy.operations(actor,project);costs.authorize(actor,input.expenseId());BigDecimal amount=FinancePolicy.amount(input.money(),5,true);FinancePolicy.reason(input.reason());
        return commands.execute(actor.id(),"POST /treasury/movements/"+id+"/payment-allocations",key,List.of(input,Objects.toString(etag,"")),Movement.class,()->{
            Set<UUID> projects=new HashSet<>(costs.projectIds(input.expenseId()));projects.add(project);policy.lock(actor,projects,false);
            var expense=costs.load(input.expenseId(),true);var m=load(id,true);Versions.check(etag,m.version());
            if(!Set.of("PAYMENT","ADVANCE").contains(m.kind())||!m.state().equals("CONFIRMED")||!expense.state().equals("VALIDATED")||expense.originalExpenseId()!=null||expense.recognitionSign()!=1)Versions.conflict("Aplique un pago o anticipo confirmado a un gasto original validado.");
            if(!Objects.equals(m.supplierId(),expense.supplierId())||!costs.projectIds(expense.id()).contains(project))Versions.invalid("El pago debe corresponder al proveedor y a la obra del gasto.");
            if(remaining(id).subtract(applied(id)).compareTo(amount)<0||costs.recognized(expense.id(),project,Instant.now()).subtract(costs.paid(expense.id(),project,Instant.now())).compareTo(amount)<0)Versions.invalid("La aplicación excede el residual del movimiento o la obligación.");
            jdbc.update("INSERT INTO payment_application(id,movement_id,expense_id,project_id,amount,application_kind,actor_id,reason) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,expense.id(),project,amount,m.kind(),actor.id(),input.reason());
            jdbc.update("UPDATE treasury_movement SET version=version+1 WHERE id=?",id);audit.record(actor.id(),"PAYMENT_APPLIED",project,Map.of("movementId",id,"expenseId",expense.id(),"reason",input.reason()));return load(id,false);
        });
    }
    private void applyCollection(Actor actor,Movement m,BigDecimal amount,String reason) {
        if(!m.kind().equals("RECEIPT")||!m.state().equals("CONFIRMED"))Versions.conflict("El cobro requiere un ingreso confirmado.");
        if(remaining(m.id()).subtract(applied(m.id())).compareTo(amount)<0)Versions.invalid("El cobro aplicado excede el ingreso disponible.");
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM contract WHERE project_id=? AND state='FORMALIZED')",Boolean.class,m.projectId())))Versions.conflict("Formalice el contrato antes de aplicar cobros.");
        if(amount.signum()>0)jdbc.update("INSERT INTO collection_application(id,movement_id,project_id,amount,actor_id,reason) VALUES (?,?,?,?,?,?)",UUID.randomUUID(),m.id(),m.projectId(),amount,actor.id(),reason);
    }
    @Transactional public Movement collect(Actor actor,UUID id,String key,String etag,CollectionInput input) {
        policy.approve(actor);UUID p=projectOf(id);BigDecimal amount=FinancePolicy.amount(input.money(),2,true);FinancePolicy.reason(input.reason());
        return commands.execute(actor.id(),"POST /treasury/movements/"+id+"/collection-allocations",key,List.of(input,Objects.toString(etag,"")),Movement.class,()->{policy.lock(actor,List.of(p),false);var m=load(id,true);Versions.check(etag,m.version());applyCollection(actor,m,amount,input.reason());jdbc.update("UPDATE treasury_movement SET version=version+1 WHERE id=?",id);audit.record(actor.id(),"COLLECTION_APPLIED",p,Map.of("movementId",id,"reason",input.reason()));return load(id,false);});
    }
    private void reduceApplications(Actor actor,Movement source,BigDecimal reduction,String reason) {
        BigDecimal toReduce=reduction.subtract(remaining(source.id()).subtract(applied(source.id())).max(BigDecimal.ZERO)).max(BigDecimal.ZERO);
        boolean receipt=source.kind().equals("RECEIPT");String table=receipt?"collection_application":"payment_application";
        var rows=jdbc.queryForList("SELECT a.*,a.amount+coalesce((SELECT sum(b.amount) FROM "+table+" b WHERE b.original_id=a.id),0) AS residual FROM "+table+" a WHERE a.movement_id=? AND a.original_id IS NULL ORDER BY a.recorded_at DESC,a.id",source.id());
        for(var row:rows) {
            BigDecimal residual=(BigDecimal)row.get("residual"),take=residual.min(toReduce);if(take.signum()<=0)continue;
            if(receipt)jdbc.update("INSERT INTO collection_application(id,movement_id,project_id,amount,original_id,actor_id,reason) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),source.id(),source.projectId(),take.negate(),row.get("id"),actor.id(),reason);
            else jdbc.update("INSERT INTO payment_application(id,movement_id,expense_id,project_id,amount,application_kind,original_id,actor_id,reason) VALUES (?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),source.id(),row.get("expense_id"),source.projectId(),take.negate(),row.get("application_kind"),row.get("id"),actor.id(),reason);
            toReduce=toReduce.subtract(take);
        }
        if(toReduce.signum()>0)throw new IllegalStateException("Application residual invariant");
        jdbc.update("UPDATE treasury_movement SET version=version+1 WHERE id=?",source.id());
    }
    @Transactional public Movement reverse(Actor actor,UUID id,String key,String etag,Reverse input) {
        policy.approve(actor);UUID p=projectOf(id);BigDecimal amount=FinancePolicy.amount(input.money(),2,true);FinancePolicy.reason(input.reason());
        return commands.execute(actor.id(),"POST /treasury/movements/"+id+"/reverse",key,List.of(input,Objects.toString(etag,"")),Movement.class,()->{
            policy.lock(actor,List.of(p),false);var m=load(id,true);Versions.check(etag,m.version());
            if(!m.state().equals("CONFIRMED")||m.transferId()!=null||m.sourceMovementId()!=null)Versions.conflict("La reversión ordinaria requiere un movimiento confirmado sin par de transferencia/devolución.");
            if(remaining(id).compareTo(amount)<0)Versions.invalid("La reversión excede el saldo reversible.");
            reduceApplications(actor,m,amount,input.reason());UUID original=jdbc.queryForList("SELECT id FROM treasury_effect WHERE movement_id=? AND original_id IS NULL",UUID.class,id).getFirst();
            jdbc.update("INSERT INTO treasury_effect(id,movement_id,amount,effective_on,original_id,actor_id,reason) VALUES (?,?,?,?,?,?,?)",UUID.randomUUID(),id,amount.negate(),LocalDate.now(FinancePolicy.ZONE),original,actor.id(),input.reason());
            if(remaining(id).signum()==0) {jdbc.update("UPDATE treasury_movement SET state='REVERSED' WHERE id=?",id);jdbc.update("UPDATE deposit_report SET state='REVERSED',version=version+1 WHERE movement_id=? AND state IN ('CONFIRMED','DUPLICATE')",id);}
            audit.record(actor.id(),"MOVEMENT_REVERSED",p,Map.of("movementId",id,"amount",amount.toPlainString(),"reason",input.reason()));return load(id,false);
        });
    }
    @Transactional public Movement transfer(Actor actor,String key,TransferInput input) {
        policy.approve(actor);FinancePolicy.amount(input.money(),2,true);FinancePolicy.date(input.operationDate());FinancePolicy.reason(input.reason());
        if(input.fromAccountId().equals(input.toAccountId())&&input.fromProjectId().equals(input.toProjectId()))Versions.invalid("Seleccione cuenta u obra de destino diferente.");
        return commands.execute(actor.id(),"POST /treasury/transfers",key,input,Movement.class,()->{
            policy.lock(actor,List.of(input.fromProjectId(),input.toProjectId()),false);UUID pair=UUID.randomUUID(),out=null;
            for(boolean incoming:List.of(false,true)) {
                UUID account=incoming?input.toAccountId():input.fromAccountId(),project=incoming?input.toProjectId():input.fromProjectId();
                if(jdbc.queryForList("SELECT id FROM treasury_account WHERE id=?",UUID.class,account).isEmpty())throw ApiException.missing();
                var i=new MovementInput(project,account,"FINANCING",null,input.money(),input.operationDate(),"OTHER","",input.factKey()+(incoming?":IN":":OUT"),null,null,input.reason());UUID id=insert(actor,i);
                // Ambos miembros se confirman en una transacción; la evidencia pertenece a la obra de origen.
                policy.evidence(actor,input.fromProjectId(),input.evidenceDocumentId(),input.reason(),"TRANSFER_"+pair);
                jdbc.update("UPDATE treasury_movement SET kind=?,transfer_id=? WHERE id=?",incoming?"TRANSFER_IN":"TRANSFER_OUT",pair,id);
                confirmFact(actor,load(id,true),null,input.reason());if(!incoming)out=id;
            }
            audit.record(actor.id(),"TREASURY_TRANSFER_CONFIRMED",input.fromProjectId(),Map.of("transferId",pair,"toProjectId",input.toProjectId(),"reason",input.reason()));return load(out,false);
        });
    }
    private Deposit deposit(UUID p,UUID id) {
        var rows=jdbc.query("SELECT d.*,EXISTS(SELECT 1 FROM deposit_report other WHERE other.project_id=d.project_id AND other.id<>d.id AND d.bank_reference<>'' AND other.bank_reference=d.bank_reference) AS coincidence FROM deposit_report d WHERE d.id=? AND d.project_id=?",(r,n)->new Deposit(id,p,FinancePolicy.money(r.getBigDecimal("money_amount")),r.getObject("operation_on",LocalDate.class),r.getString("payment_method"),r.getString("bank_reference"),r.getObject("evidence_id",UUID.class),r.getString("note"),r.getString("state"),r.getObject("movement_id",UUID.class),r.getLong("version"),r.getString("decision_reason"),r.getBoolean("coincidence")),id,p);if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    public Deposit readDeposit(Actor actor,UUID p,UUID id) {policy.collections(actor,p);return deposit(p,id);}
    public List<Deposit> deposits(Actor actor,UUID p,int page,int size) {policy.collections(actor,p);CostService.pagination(page,size);return jdbc.queryForList("SELECT id FROM deposit_report WHERE project_id=? ORDER BY created_at,id LIMIT ? OFFSET ?",UUID.class,p,size,page*size).stream().map(id->deposit(p,id)).toList();}
    @Transactional public Deposit report(Actor actor,UUID p,String key,DepositInput input) {
        policy.collections(actor,p);BigDecimal amount=FinancePolicy.amount(input.money(),2,true);FinancePolicy.date(input.operationDate());
        return commands.execute(actor.id(),"POST /projects/"+p+"/deposit-reports",key,input,Deposit.class,()->{
            policy.lock(actor,List.of(p),true);UUID id=UUID.randomUUID();policy.evidence(actor,p,input.evidenceDocumentId(),"","DEPOSIT_"+id);
            jdbc.update("INSERT INTO deposit_report(id,project_id,money_amount,currency,operation_on,payment_method,bank_reference,evidence_id,note,created_by) VALUES (?,?,?,'CRC',?,?,?,?,?,?)",id,p,amount,input.operationDate(),input.paymentMethod(),input.bankReference(),input.evidenceDocumentId(),input.note(),actor.id());audit.record(actor.id(),"DEPOSIT_REPORTED",p,Map.of("depositId",id));return deposit(p,id);
        });
    }
    @Transactional public Deposit reviewDeposit(Actor actor,UUID p,UUID id,String key,String etag,String action,Decision input) {
        policy.operations(actor,p);if(action.equals("reject"))policy.approve(actor);FinancePolicy.reason(input.reason());
        return commands.execute(actor.id(),"POST /projects/"+p+"/deposit-reports/"+id+"/"+action,key,List.of(input,Objects.toString(etag,"")),Deposit.class,()->{
            policy.lock(actor,List.of(p),false);var d=deposit(p,id);Versions.check(etag,d.version());if(!Set.of("REPORTED","IN_REVIEW").contains(d.state()))Versions.conflict("El reporte ya fue decidido.");
            jdbc.update("UPDATE deposit_report SET state=?,decision_reason=?,version=version+1 WHERE id=?",action.equals("review")?"IN_REVIEW":"REJECTED",input.reason(),id);audit.record(actor.id(),"DEPOSIT_"+action.toUpperCase(Locale.ROOT),p,Map.of("depositId",id,"reason",input.reason()));return deposit(p,id);
        });
    }
    @Transactional public Deposit confirmDeposit(Actor actor,UUID p,UUID id,String key,String etag,ConfirmDeposit input) {
        policy.approve(actor);policy.operations(actor,p);BigDecimal applied=FinancePolicy.amount(input.appliedMoney(),2,false);FinancePolicy.reason(input.reason());
        return commands.execute(actor.id(),"POST /projects/"+p+"/deposit-reports/"+id+"/confirm",key,List.of(input,Objects.toString(etag,"")),Deposit.class,()->{
            policy.lock(actor,List.of(p),false);var d=deposit(p,id);Versions.check(etag,d.version());if(!Set.of("REPORTED","IN_REVIEW").contains(d.state()))Versions.conflict("El reporte ya fue decidido.");
            UUID movement;String state;
            if(input.existingMovementId()!=null) {
                if(input.accountId()!=null||input.factKey()!=null||applied.signum()!=0)Versions.invalid("Vincular un duplicado no crea una imputación ni un nuevo hecho.");
                if(!projectOf(input.existingMovementId()).equals(p))throw ApiException.missing();var m=load(input.existingMovementId(),true);
                if(!m.kind().equals("RECEIPT")||!m.state().equals("CONFIRMED")||m.money().decimal().compareTo(d.money().decimal())!=0||!m.operationDate().equals(d.operationDate()))Versions.invalid("El reporte no corresponde al ingreso existente.");
                movement=m.id();state="DUPLICATE";
            } else {
                if(input.accountId()==null||input.factKey()==null)Versions.invalid("Indique cuenta e identidad del hecho.");
                var i=new MovementInput(p,input.accountId(),"RECEIPT",null,d.money(),d.operationDate(),d.paymentMethod(),d.bankReference(),input.factKey(),null,input.evidenceDocumentId(),input.reason());movementInput(i);movement=insert(actor,i);confirmFact(actor,load(movement,true),input.evidenceDocumentId(),input.reason());applyCollection(actor,load(movement,false),applied,input.reason());state="CONFIRMED";
            }
            jdbc.update("UPDATE deposit_report SET state=?,movement_id=?,decision_reason=?,version=version+1 WHERE id=?",state,movement,input.reason(),id);audit.record(actor.id(),"DEPOSIT_"+state,p,Map.of("depositId",id,"movementId",movement,"reason",input.reason()));return deposit(p,id);
        });
    }
}
