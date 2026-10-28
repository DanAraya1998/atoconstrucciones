package com.daraya.atoconstrucciones.costs;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.finance.FinancePolicy;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class CostService {
    public record AllocationInput(@NotNull UUID projectId,UUID extraId,@NotNull Money net,@NotNull Money tax) {}
    public record Input(UUID supplierId,@NotNull @Size(max=1000) String supplierException,@NotNull LocalDate accruedOn,
        @NotBlank @Size(max=60) String category,@NotBlank @Size(max=1000) String description,
        @NotNull Money net,@NotNull Money tax,UUID voucherId,@Pattern(regexp="[0-9]{50}") String fiscalKey,
        UUID originalExpenseId,@NotNull @Size(max=1000) String differenceReason,UUID evidenceDocumentId,
        @NotEmpty @Size(max=100) List<@Valid AllocationInput> allocations) {}
    public record Decision(UUID evidenceDocumentId,@NotBlank @Size(max=1000) String reason) {}
    public record Allocation(UUID id,UUID projectId,UUID extraId,Money net,Money tax,Money total) {}
    public record Expense(UUID id,UUID supplierId,LocalDate accruedOn,String category,String description,Money net,Money tax,Money total,
        UUID voucherId,String fiscalKey,UUID originalExpenseId,int recognitionSign,String state,long version,List<Allocation> allocations,
        String differenceReason,String decisionReason,String supplierException,UUID evidenceDocumentId) {}
    public record ProjectExpense(UUID id,UUID supplierId,String description,String category,LocalDate accruedOn,String state,long version,
        Money allocatedTotal,Money recognizedCost,Money appliedPayments,Money due,Money supplierCredit) {}
    private final JdbcTemplate jdbc;
    private final FinancePolicy policy;
    private final IdempotencyService commands;
    private final AuditService audit;
    public CostService(JdbcTemplate jdbc,FinancePolicy policy,IdempotencyService commands,AuditService audit) {
        this.jdbc=jdbc;this.policy=policy;this.commands=commands;this.audit=audit;
    }
    public List<Allocation> allocations(UUID expense) {
        return jdbc.query("SELECT a.* FROM expense_allocation a JOIN expense e ON e.id=a.expense_id WHERE a.expense_id=? AND a.allocation_revision=e.allocation_revision ORDER BY a.project_id,a.id",(r,n)->new Allocation(r.getObject("id",UUID.class),r.getObject("project_id",UUID.class),r.getObject("extra_id",UUID.class),
            FinancePolicy.money(r.getBigDecimal("net_amount")),FinancePolicy.money(r.getBigDecimal("tax_amount")),FinancePolicy.money(r.getBigDecimal("gross_amount"))),expense);
    }
    public List<UUID> projectIds(UUID expense) { var ids=allocations(expense).stream().map(Allocation::projectId).distinct().toList(); if(ids.isEmpty())throw ApiException.missing();return ids; }
    public void authorize(Actor actor,UUID expense) { for(UUID p:projectIds(expense))policy.operations(actor,p); }
    public Expense read(Actor actor,UUID id) { authorize(actor,id);return load(id,false); }
    public Expense load(UUID id,boolean lock) {
        var rows=jdbc.query("SELECT * FROM expense WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new Expense(id,r.getObject("supplier_id",UUID.class),r.getObject("accrued_on",LocalDate.class),r.getString("category"),r.getString("description"),
            FinancePolicy.money(r.getBigDecimal("net_amount")),FinancePolicy.money(r.getBigDecimal("tax_amount")),FinancePolicy.money(r.getBigDecimal("gross_amount")),r.getObject("voucher_id",UUID.class),r.getString("fiscal_key"),r.getObject("original_expense_id",UUID.class),
            r.getInt("recognition_sign"),r.getString("state"),r.getLong("version"),allocations(id),r.getString("difference_reason"),r.getString("decision_reason"),r.getString("supplier_exception"),r.getObject("evidence_id",UUID.class)),id);
        if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    public BigDecimal recognized(UUID expense,UUID project,Instant cut) {
        return jdbc.queryForObject("SELECT coalesce(sum(c.amount),0) FROM cost_recognition c JOIN expense e ON e.id=c.expense_id WHERE (e.id=? OR e.original_expense_id=?) AND c.project_id=? AND c.recorded_at<=? AND c.effective_on<=?",BigDecimal.class,expense,expense,project,java.sql.Timestamp.from(cut),cut.atZone(FinancePolicy.ZONE).toLocalDate());
    }
    public BigDecimal paid(UUID expense,UUID project,Instant cut) { return jdbc.queryForObject("SELECT coalesce(sum(amount),0) FROM payment_application WHERE expense_id=? AND project_id=? AND recorded_at<=?",BigDecimal.class,expense,project,java.sql.Timestamp.from(cut)); }
    public List<ProjectExpense> list(Actor actor,UUID project,int page,int size) {
        policy.operations(actor,project); pagination(page,size);Instant cut=Instant.now();
        return jdbc.query("SELECT e.*,sum(a.gross_amount) AS allocated FROM expense e JOIN expense_allocation a ON a.expense_id=e.id WHERE a.project_id=? AND a.allocation_revision=e.allocation_revision GROUP BY e.id ORDER BY e.created_at,e.id LIMIT ? OFFSET ?",(r,n)->{
            UUID id=r.getObject("id",UUID.class);BigDecimal recognized=recognized(id,project,cut),paid=paid(id,project,cut);
            return new ProjectExpense(id,r.getObject("supplier_id",UUID.class),r.getString("description"),r.getString("category"),r.getObject("accrued_on",LocalDate.class),r.getString("state"),r.getLong("version"),FinancePolicy.money(r.getBigDecimal("allocated")),FinancePolicy.money(recognized),FinancePolicy.money(paid),FinancePolicy.money(recognized.subtract(paid).max(BigDecimal.ZERO)),FinancePolicy.money(paid.subtract(recognized).max(BigDecimal.ZERO)));
        },project,size,page*size);
    }
    public static void pagination(int page,int size) { if(page<0||page>100000||size<1||size>100)throw new ApiException(400,"INVALID_PAGINATION","Paginación inválida."); }
    @Transactional
    public Expense create(Actor actor,String key,Input input) {return save(actor,null,key,null,input);}
    @Transactional public Expense edit(Actor actor,UUID id,String key,String etag,Input input) {authorize(actor,id);return save(actor,id,key,etag,input);}
    private Expense save(Actor actor,UUID existingId,String key,String etag,Input input) {
        if(input.allocations()==null||input.allocations().isEmpty()||input.allocations().size()>100)Versions.invalid("Indique las imputaciones.");
        List<UUID> projects=input.allocations().stream().map(AllocationInput::projectId).toList();for(UUID p:projects)policy.operations(actor,p);
        Set<UUID> lockedProjects=new HashSet<>(projects);if(existingId!=null)lockedProjects.addAll(projectIds(existingId));if(input.voucherId()!=null){var origin=jdbc.queryForList("SELECT project_id FROM fiscal_voucher WHERE id=?",UUID.class,input.voucherId());if(origin.isEmpty())throw ApiException.missing();policy.operations(actor,origin.getFirst());lockedProjects.add(origin.getFirst());}if(input.originalExpenseId()!=null){authorize(actor,input.originalExpenseId());lockedProjects.addAll(projectIds(input.originalExpenseId()));}
        FinancePolicy.date(input.accruedOn());BigDecimal net=FinancePolicy.amount(input.net(),5,false),tax=FinancePolicy.amount(input.tax(),5,false),total=net.add(tax);
        if(total.signum()<=0||total.precision()-total.scale()>18)Versions.invalid("El gasto debe ser positivo.");
        if(input.supplierId()==null)FinancePolicy.reason(input.supplierException());
        return commands.execute(actor.id(),existingId==null?"POST /expenses":"PATCH /expenses/"+existingId,key,existingId==null?input:List.of(input,Objects.toString(etag,"")),Expense.class,()->{
            policy.lock(actor,lockedProjects,false);
            if(existingId!=null){var current=load(existingId,true);Versions.check(etag,current.version());if(!Set.of("REGISTERED","IN_REVIEW").contains(current.state()))Versions.conflict("Solo una propuesta sin validar admite edición.");}
            if(input.supplierId()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM party WHERE id=? AND kind='SUPPLIER' AND active)",Boolean.class,input.supplierId())))throw ApiException.missing();
            int sign=1;String fiscalKey=input.fiscalKey();
            if(fiscalKey!=null&&!fiscalKey.matches("[0-9]{50}"))Versions.invalid("Clave fiscal inválida.");
            if(input.voucherId()!=null) {
                var vouchers=jdbc.queryForList("SELECT * FROM fiscal_voucher WHERE id=? FOR UPDATE",input.voucherId());if(vouchers.isEmpty())throw ApiException.missing();var v=vouchers.getFirst();
                policy.operations(actor,(UUID)v.get("project_id"));
                if(!v.get("currency").equals("CRC"))Versions.invalid("El comprobante requiere conversión de moneda.");
                if(net.compareTo((BigDecimal)v.get("net_amount"))!=0||tax.compareTo((BigDecimal)v.get("tax_amount"))!=0||total.compareTo((BigDecimal)v.get("gross_amount"))!=0)Versions.invalid("Los importes deben conservar el total fiscal; distribuya su porción mediante imputaciones.");
                fiscalKey=(String)v.get("fiscal_key");String type=(String)v.get("voucher_type");
                if(Set.of("NotaCreditoElectronica","NotaDebitoElectronica").contains(type)) {
                    if(input.originalExpenseId()==null)Versions.invalid("Vincule la nota al gasto original.");authorize(actor,input.originalExpenseId());var original=load(input.originalExpenseId(),true);
                    if(!original.state().equals("VALIDATED")||original.recognitionSign()!=1||original.originalExpenseId()!=null||!Objects.equals(original.fiscalKey(),v.get("reference_key")))Versions.invalid("La referencia fiscal no corresponde al gasto original validado.");
                    if(!Objects.equals(original.supplierId(),input.supplierId()))Versions.invalid("La nota debe conservar el proveedor original.");
                    if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM fiscal_voucher o JOIN fiscal_voucher n ON n.id=? WHERE o.fiscal_key=? AND o.extraction->'issuer'->>'number'=n.extraction->'issuer'->>'number' AND o.extraction->'issuer'->>'type'=n.extraction->'issuer'->>'type' AND o.extraction->'receiver' IS NOT DISTINCT FROM n.extraction->'receiver')",Boolean.class,input.voucherId(),original.fiscalKey())))Versions.invalid("Importe el original y coteje emisor y receptor de la nota.");
                    sign=type.equals("NotaCreditoElectronica")?-1:1;
                } else if(input.originalExpenseId()!=null)Versions.invalid("Solo una NC/ND puede ajustar el original.");
            } else if(input.originalExpenseId()!=null)Versions.invalid("Un ajuste fiscal requiere comprobante importado.");
            if(fiscalKey!=null) {
                jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,"fiscal-identity:"+fiscalKey);
                if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM expense WHERE fiscal_key=? AND (?::uuid IS NULL OR id<>?::uuid))",Boolean.class,fiscalKey,existingId,existingId)))throw new ApiException(409,"DUPLICATE_FISCAL_KEY","La factura ya tiene un gasto; adjunte las evidencias al original.");
            }
            BigDecimal allocatedNet=BigDecimal.ZERO,allocatedTax=BigDecimal.ZERO;Set<String> unique=new HashSet<>();
            for(var a:input.allocations()) {
                BigDecimal an=FinancePolicy.amount(a.net(),5,false),at=FinancePolicy.amount(a.tax(),5,false);if(an.add(at).signum()<=0)Versions.invalid("Cada imputación debe ser positiva.");
                if(!unique.add(a.projectId()+":"+a.extraId()))Versions.invalid("Combine las imputaciones repetidas de obra/extra.");
                if(a.extraId()!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM extra_request WHERE id=? AND project_id=?)",Boolean.class,a.extraId(),a.projectId())))throw ApiException.missing();
                if(input.originalExpenseId()!=null&&!projectIds(input.originalExpenseId()).contains(a.projectId()))Versions.invalid("La nota solo afecta obras del original.");
                allocatedNet=allocatedNet.add(an);allocatedTax=allocatedTax.add(at);
            }
            if(allocatedNet.compareTo(net)>0||allocatedTax.compareTo(tax)>0)Versions.invalid("Las imputaciones exceden base o impuestos de la factura.");
            if(allocatedNet.compareTo(net)!=0||allocatedTax.compareTo(tax)!=0)FinancePolicy.reason(input.differenceReason());
            UUID id=existingId==null?UUID.randomUUID():existingId;int revision=existingId==null?1:jdbc.queryForObject("SELECT allocation_revision+1 FROM expense WHERE id=?",Integer.class,id);
            if(existingId==null)jdbc.update("INSERT INTO expense(id,supplier_id,supplier_exception,accrued_on,category,description,currency,net_amount,tax_amount,gross_amount,voucher_id,fiscal_key,original_expense_id,recognition_sign,difference_reason,evidence_id,created_by) VALUES (?,?,?,?,?,?,'CRC',?,?,?,?,?,?,?,?,?,?)",
                id,input.supplierId(),input.supplierException(),input.accruedOn(),input.category(),input.description(),net,tax,total,input.voucherId(),fiscalKey,input.originalExpenseId(),sign,input.differenceReason(),input.evidenceDocumentId(),actor.id());
            else jdbc.update("UPDATE expense SET supplier_id=?,supplier_exception=?,accrued_on=?,category=?,description=?,net_amount=?,tax_amount=?,gross_amount=?,voucher_id=?,fiscal_key=?,original_expense_id=?,recognition_sign=?,difference_reason=?,evidence_id=?,state='REGISTERED',allocation_revision=?,version=version+1 WHERE id=?",input.supplierId(),input.supplierException(),input.accruedOn(),input.category(),input.description(),net,tax,total,input.voucherId(),fiscalKey,input.originalExpenseId(),sign,input.differenceReason(),input.evidenceDocumentId(),revision,id);
            jdbc.update("INSERT INTO expense_revision(expense_id,revision,input,actor_id) VALUES (?,?,?::jsonb,?)",id,revision,tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(input),actor.id());
            for(var a:input.allocations())jdbc.update("INSERT INTO expense_allocation(id,expense_id,project_id,extra_id,allocation_revision,net_amount,tax_amount,gross_amount) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,a.projectId(),a.extraId(),revision,a.net().decimal(),a.tax().decimal(),a.net().decimal().add(a.tax().decimal()));
            if(input.evidenceDocumentId()!=null)policy.evidenceAcross(actor,projects,input.evidenceDocumentId(),"","EXPENSE_"+id);
            audit.record(actor.id(),"EXPENSE_REGISTERED",projects.getFirst(),Map.of("expenseId",id));return load(id,false);
        });
    }
    @Transactional
    public Expense decide(Actor actor,UUID id,String key,String etag,String action,Decision input) {
        authorize(actor,id);if(!action.equals("review"))policy.approve(actor);FinancePolicy.reason(input.reason());
        return commands.execute(actor.id(),"POST /expenses/"+id+"/"+action,key,List.of(input,Objects.toString(etag,"")),Expense.class,()->{
            Set<UUID> lockedProjects=new HashSet<>(projectIds(id));var beforeLock=load(id,false);if(beforeLock.originalExpenseId()!=null)lockedProjects.addAll(projectIds(beforeLock.originalExpenseId()));
            policy.lock(actor,lockedProjects,false);
            // Ajustes toman original antes de la nota; todas sus obras ya están bloqueadas.
            Expense before=load(id,false);if(before.originalExpenseId()!=null)load(before.originalExpenseId(),true);
            Expense e=load(id,true);Versions.check(etag,e.version());
            String state;
            if(action.equals("reverse")) {
                if(!e.state().equals("VALIDATED"))Versions.conflict("Solo un costo validado admite reversión.");
                if(e.originalExpenseId()==null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM expense WHERE original_expense_id=? AND state='VALIDATED')",Boolean.class,id)))Versions.conflict("Revierta primero las notas vinculadas al original.");
                for(var a:e.allocations()) {
                    var original=jdbc.queryForList("SELECT * FROM cost_recognition WHERE expense_id=? AND allocation_id=? AND original_id IS NULL",id,a.id()).getFirst();
                    jdbc.update("INSERT INTO cost_recognition(id,expense_id,allocation_id,project_id,amount,net_amount,tax_amount,original_id,effective_on,actor_id,reason) VALUES (?,?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,a.id(),a.projectId(),((BigDecimal)original.get("amount")).negate(),((BigDecimal)original.get("net_amount")).negate(),((BigDecimal)original.get("tax_amount")).negate(),original.get("id"),LocalDate.now(FinancePolicy.ZONE),actor.id(),input.reason());
                }
                state="REVERSED";
            } else {
                if(!Set.of("REGISTERED","IN_REVIEW").contains(e.state()))Versions.conflict("El gasto ya fue decidido.");
                state=switch(action){case "review"->"IN_REVIEW";case "reject"->"REJECTED";case "validate"->"VALIDATED";default->throw ApiException.missing();};
                if(action.equals("validate")) {
                    var row=jdbc.queryForMap("SELECT evidence_id FROM expense WHERE id=?",id);UUID evidence=input.evidenceDocumentId()!=null?input.evidenceDocumentId():(UUID)row.get("evidence_id");
                    policy.evidenceAcross(actor,projectIds(id),evidence,input.reason(),"COST_VALIDATION_"+id);
                    for(var a:e.allocations()) {
                        BigDecimal factor=BigDecimal.valueOf(e.recognitionSign());
                        if(e.recognitionSign()<0&&recognized(e.originalExpenseId(),a.projectId(),Instant.now()).compareTo(a.total().decimal())<0)Versions.invalid("El crédito excede el costo original reversible en esta obra.");
                        if(e.recognitionSign()<0) {
                            var balance=jdbc.queryForMap("SELECT coalesce(sum(c.net_amount),0) AS net,coalesce(sum(c.tax_amount),0) AS tax FROM cost_recognition c JOIN expense x ON x.id=c.expense_id WHERE (x.id=? OR x.original_expense_id=?) AND c.project_id=?",e.originalExpenseId(),e.originalExpenseId(),a.projectId());
                            if(((BigDecimal)balance.get("net")).compareTo(a.net().decimal())<0||((BigDecimal)balance.get("tax")).compareTo(a.tax().decimal())<0)Versions.invalid("El crédito excede base o impuestos reconocidos del original.");
                        }
                        jdbc.update("INSERT INTO cost_recognition(id,expense_id,allocation_id,project_id,amount,net_amount,tax_amount,effective_on,actor_id,reason) VALUES (?,?,?,?,?,?,?,?,?,?)",UUID.randomUUID(),id,a.id(),a.projectId(),a.total().decimal().multiply(factor),a.net().decimal().multiply(factor),a.tax().decimal().multiply(factor),e.accruedOn(),actor.id(),input.reason());
                    }
                }
            }
            if(action.equals("reverse"))jdbc.update("UPDATE expense SET state=?,version=version+1 WHERE id=?",state,id);
            else jdbc.update("UPDATE expense SET state=?,version=version+1,decided_at=clock_timestamp(),decision_reason=? WHERE id=?",state,input.reason(),id);
            audit.record(actor.id(),"EXPENSE_"+state,e.allocations().getFirst().projectId(),Map.of("expenseId",id,"reason",input.reason(),"fromVersion",e.version(),"toVersion",e.version()+1));return load(id,false);
        });
    }
}
