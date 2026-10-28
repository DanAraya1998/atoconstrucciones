package com.daraya.atoconstrucciones.contracts;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.identity.SensitiveAuthenticationPolicy;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.documents.DocumentService;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class ContractService {
    public record ScopeChanged(Actor actor,UUID projectId) {}
    public record Input(@NotBlank @Size(max=80) String number,@NotBlank @Size(max=2000) String scope,
        @NotNull @Valid Money net,@NotNull @Valid Money tax,@NotNull LocalDate startsOn,@NotNull LocalDate endsOn) {}
    public record Evidence(@NotNull UUID evidenceId,@NotBlank @Size(max=1000) String reason) {}
    public record Request(@NotBlank @Size(max=2000) String scope) {}
    public record Revision(@NotBlank @Size(max=2000) String scope,@NotNull @Valid Money net,@NotNull @Valid Money tax,
                           @Min(-3650) @Max(3650) int daysDelta) {}
    public record Adjustment(@NotNull @Valid Money netDelta,@NotNull @Valid Money taxDelta,@Min(-3650) @Max(3650) int daysDelta,
                             @NotNull UUID evidenceId,@NotBlank @Size(max=1000) String reason) {}
    public record Amendment(UUID id,UUID extraId,Integer revision,String kind,Money netDelta,Money taxDelta,int daysDelta,
                            UUID evidenceId,String reason,UUID reversalOf,Instant effectiveAt) {}
    public record Contract(UUID projectId,String number,String scope,Money net,Money tax,Money initialTotal,Money currentTotal,
        LocalDate startsOn,LocalDate endsOn,LocalDate currentEndsOn,String state,UUID evidenceId,long version,List<Amendment> amendments,Instant asOf,Instant formalizedAt) {}
    public record ExtraRevision(int revision,String scope,Money net,Money tax,int daysDelta,UUID acceptanceEvidence,UUID acceptedBy) {}
    public record Extra(UUID id,UUID projectId,String scope,String origin,String state,int currentRevision,long version,List<ExtraRevision> revisions) {}
    public record ExtraPage(List<Extra> items) {}
    private final JdbcTemplate jdbc;private final ProjectService projects;private final DocumentService documents;
    private final AuditService audit;private final IdempotencyService commands;
    private final SensitiveAuthenticationPolicy sensitive;
    private final ApplicationEventPublisher events;
    public ContractService(JdbcTemplate jdbc,ProjectService projects,DocumentService documents,AuditService audit,IdempotencyService commands,SensitiveAuthenticationPolicy sensitive,ApplicationEventPublisher events){this.sensitive=sensitive;this.events=events;
        this.jdbc=jdbc;this.projects=projects;this.documents=documents;this.audit=audit;this.commands=commands;
    }
    private Money money(BigDecimal n){return new Money(n.setScale(2).toPlainString(),"CRC");}
    private BigDecimal decimal(Money m,boolean signed){
        if(!m.currency().equals("CRC"))Versions.invalid("M1 admite únicamente CRC.");
        BigDecimal n=m.decimal();if(n.scale()>2||n.precision()-n.scale()>18||!signed&&n.signum()<0)Versions.invalid("Importe fuera de rango o con más de dos decimales.");return n;
    }
    private void allowed(Actor actor,UUID projectId){projects.authorize(actor,projectId);if(!actor.manager()&&!projects.capacity(actor,projectId,"OPERATIONS")&&!projects.capacity(actor,projectId,"CLIENT"))throw ApiException.forbidden();}
    private Contract load(UUID id){return load(id,Instant.now());}
    private Contract load(UUID id,Instant asOf){
        var rows=jdbc.queryForList("SELECT * FROM contract WHERE project_id=?",id);if(rows.isEmpty())throw ApiException.missing();var r=rows.getFirst();
        var amendments=jdbc.query("SELECT * FROM contract_amendment WHERE project_id=? AND effective_at<=? ORDER BY effective_at,id",(a,n)->new Amendment(a.getObject("id",UUID.class),a.getObject("extra_id",UUID.class),(Integer)a.getObject("revision"),a.getString("kind"),money(a.getBigDecimal("net_delta")),money(a.getBigDecimal("tax_delta")),a.getInt("days_delta"),a.getObject("evidence_id",UUID.class),a.getString("reason"),a.getObject("reversal_of",UUID.class),a.getTimestamp("effective_at").toInstant()),id,java.sql.Timestamp.from(asOf));
        BigDecimal net=(BigDecimal)r.get("net_amount"),tax=(BigDecimal)r.get("tax_amount"),initial=net.add(tax);
        BigDecimal delta=amendments.stream().map(a->a.netDelta().decimal().add(a.taxDelta().decimal())).reduce(BigDecimal.ZERO,BigDecimal::add);
        LocalDate end=((java.sql.Date)r.get("ends_on")).toLocalDate();
        return new Contract(id,r.get("number").toString(),r.get("scope").toString(),money(net),money(tax),money(initial),money(initial.add(delta)),
            ((java.sql.Date)r.get("starts_on")).toLocalDate(),end,end.plusDays(amendments.stream().mapToInt(Amendment::daysDelta).sum()),r.get("state").toString(),(UUID)r.get("evidence_id"),(Long)r.get("version")+amendments.size(),amendments,asOf,r.get("formalized_at")==null?null:((java.sql.Timestamp)r.get("formalized_at")).toInstant());
    }
    public Contract read(Actor actor,UUID id){allowed(actor,id);return load(id);}
    public Contract read(Actor actor,UUID id,Instant asOf){
        allowed(actor,id);if(asOf==null)return load(id);
        if(asOf.isAfter(Instant.now()))Versions.invalid("El corte no puede estar en el futuro.");
        Contract c=load(id,asOf);if(c.formalizedAt()==null||c.formalizedAt().isAfter(asOf))Versions.invalid("No existe contrato formalizado para ese corte.");return c;
    }
    @Transactional
    public Contract save(Actor actor,UUID id,String key,String etag,Input input){
        actor.requireManager();BigDecimal net=decimal(input.net(),false),tax=decimal(input.tax(),false);
        if(net.add(tax).compareTo(new BigDecimal("999999999999999999.99"))>0)Versions.invalid("El total contractual excede el rango admitido.");
        if(input.startsOn().isAfter(input.endsOn()))Versions.invalid("Plazo contractual inválido.");
        return commands.execute(actor.id(),"PUT /projects/"+id+"/contract",key,List.of(input,Objects.toString(etag,"")),Contract.class,()->{
            var p=projects.lock(actor,id);projects.writable(p);
            if(!p.state().equals("BORRADOR"))Versions.conflict("La base solo se prepara en borrador.");
            var existing=jdbc.queryForList("SELECT state,version FROM contract WHERE project_id=?",id);
            if(!existing.isEmpty()){
                Versions.check(etag,(Long)existing.getFirst().get("version"));
                if(!existing.getFirst().get("state").equals("DRAFT"))Versions.conflict("La base formalizada es inmutable; registre una enmienda.");
            } else Versions.check(etag,p.version());
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM contract WHERE number=? AND project_id<>?)",Boolean.class,input.number(),id)))throw new ApiException(409,"DUPLICATE_CONTRACT_NUMBER","El número contractual ya está en uso.");
            jdbc.update("""
                INSERT INTO contract(project_id,number,scope,net_amount,tax_amount,currency,starts_on,ends_on) VALUES (?,?,?,?,?,'CRC',?,?)
                ON CONFLICT(project_id) DO UPDATE SET number=excluded.number,scope=excluded.scope,net_amount=excluded.net_amount,tax_amount=excluded.tax_amount,starts_on=excluded.starts_on,ends_on=excluded.ends_on,version=contract.version+1
                """,id,input.number().trim(),input.scope().trim(),input.net().decimal(),input.tax().decimal(),input.startsOn(),input.endsOn());
            audit.record(actor.id(),"CONTRACT_DRAFT_SAVED",id);return load(id);
        });
    }
    @Transactional
    public Contract formalize(Actor actor,UUID id,String key,String etag,Evidence input){
        actor.requireManager();sensitive.require(actor);
        return commands.execute(actor.id(),"POST /projects/"+id+"/contract/formalize",key,List.of(input,Objects.toString(etag,"")),Contract.class,()->{
            projects.writable(projects.lock(actor,id));Contract c=load(id);Versions.check(etag,c.version());
            if(!c.state().equals("DRAFT"))Versions.conflict("El contrato ya está formalizado.");
            documents.requireEvidence(actor,id,input.evidenceId());documents.retain(actor,id,input.evidenceId(),"CONTRACT_BASE");
            jdbc.update("UPDATE contract SET state='FORMALIZED',evidence_id=?,formalized_by=?,formalized_at=now(),version=version+1 WHERE project_id=?",input.evidenceId(),actor.id(),id);
            audit.record(actor.id(),"CONTRACT_FORMALIZED",id,Map.of("evidenceId",input.evidenceId(),"reason",input.reason(),"fromVersion",c.version(),"toVersion",c.version()+1));return load(id);
        });
    }
    private Extra extra(UUID projectId,UUID id,boolean lock){
        var rows=jdbc.queryForList("SELECT * FROM extra_request WHERE project_id=? AND id=?"+(lock?" FOR UPDATE":""),projectId,id);
        if(rows.isEmpty())throw ApiException.missing();var r=rows.getFirst();
        var revisions=jdbc.query("SELECT r.*,a.evidence_id,a.actor_id FROM extra_revision r LEFT JOIN extra_acceptance a ON a.extra_id=r.extra_id AND a.revision=r.revision WHERE r.extra_id=? ORDER BY r.revision",(s,n)->new ExtraRevision(s.getInt("revision"),s.getString("scope"),money(s.getBigDecimal("net_amount")),money(s.getBigDecimal("tax_amount")),s.getInt("days_delta"),s.getObject("evidence_id",UUID.class),s.getObject("actor_id",UUID.class)),id);
        return new Extra(id,projectId,r.get("scope").toString(),r.get("origin").toString(),r.get("state").toString(),(Integer)r.get("current_revision"),(Long)r.get("version"),revisions);
    }
    public Extra readExtra(Actor actor,UUID p,UUID id){allowed(actor,p);return extra(p,id,false);}
    public ExtraPage extras(Actor actor,UUID p){allowed(actor,p);return new ExtraPage(jdbc.queryForList("SELECT id FROM extra_request WHERE project_id=? ORDER BY created_at,id",UUID.class,p).stream().map(id->extra(p,id,false)).toList());}
    /** Alcance para planificación: un extra parcialmente liquidado conserva el trabajo contractual. */
    public record SchedulingScope(List<UUID> extraIds,List<UUID> amendmentIds) {}
    public SchedulingScope schedulingScope(Actor actor,UUID p) {
        projects.authorize(actor,p);
        var extras=jdbc.queryForList("SELECT e.id FROM extra_request e WHERE e.project_id=? AND (e.state IN ('APROBADO','EN_EJECUCION','TERMINADO') OR EXISTS(SELECT 1 FROM contract_amendment a WHERE a.extra_id=e.id GROUP BY a.extra_id HAVING sum(a.net_delta+a.tax_delta)>0)) ORDER BY e.id",UUID.class,p);
        return new SchedulingScope(extras,jdbc.queryForList("SELECT id FROM contract_amendment WHERE project_id=? ORDER BY id",UUID.class,p));
    }
    @Transactional
    public Extra request(Actor actor,UUID p,String key,Request input){
        allowed(actor,p);return commands.execute(actor.id(),"POST /projects/"+p+"/extras",key,input,Extra.class,()->{
            projects.writable(projects.lock(actor,p));UUID id=UUID.randomUUID();
            String origin=actor.manager()?"MANAGER":projects.capacity(actor,p,"OPERATIONS")?"ON_BEHALF":"CLIENT";
            jdbc.update("INSERT INTO extra_request(id,project_id,requested_by,origin,scope) VALUES (?,?,?,?,?)",id,p,actor.id(),origin,input.scope().trim());
            audit.record(actor.id(),"EXTRA_REQUESTED",p,Map.of("extraId",id,"origin",origin));return extra(p,id,false);
        });
    }
    @Transactional
    public Extra revise(Actor actor,UUID p,UUID id,String key,String etag,Revision input){
        projects.operations(actor,p);decimal(input.net(),false);decimal(input.tax(),false);
        return commands.execute(actor.id(),"POST /projects/"+p+"/extras/"+id+"/revisions",key,List.of(input,Objects.toString(etag,"")),Extra.class,()->{
            projects.writable(projects.lock(actor,p));Extra e=extra(p,id,true);Versions.check(etag,e.version());
            if(!Set.of("SOLICITADO","PENDIENTE_APROBACION").contains(e.state()))Versions.conflict("El extra no admite otra cotización.");
            int revision=e.currentRevision()+1;
            jdbc.update("INSERT INTO extra_revision(extra_id,revision,scope,net_amount,tax_amount,currency,days_delta,authored_by) VALUES (?,?,?,?,?,'CRC',?,?)",id,revision,input.scope().trim(),input.net().decimal(),input.tax().decimal(),input.daysDelta(),actor.id());
            jdbc.update("UPDATE extra_request SET current_revision=?,state='PENDIENTE_APROBACION',version=version+1 WHERE id=?",revision,id);
            audit.record(actor.id(),"EXTRA_REVISED",p,Map.of("extraId",id,"revision",revision));return extra(p,id,false);
        });
    }
    @Transactional
    public Extra accept(Actor actor,UUID p,UUID id,String key,String etag,Evidence input){
        allowed(actor,p);if(!actor.manager())projects.representative(actor,p);else sensitive.require(actor);
        return commands.execute(actor.id(),"POST /projects/"+p+"/extras/"+id+"/acceptances",key,List.of(input,Objects.toString(etag,"")),Extra.class,()->{
            projects.writable(projects.lock(actor,p));Extra e=extra(p,id,true);Versions.check(etag,e.version());
            if(!e.state().equals("PENDIENTE_APROBACION"))Versions.conflict("Debe existir una revisión pendiente.");
            documents.requireEvidence(actor,p,input.evidenceId());
            if(e.revisions().getLast().acceptanceEvidence()!=null)Versions.conflict("Esta revisión ya tiene aceptación.");
            documents.retain(actor,p,input.evidenceId(),"EXTRA_ACCEPTANCE_"+id+"_"+e.currentRevision());
            jdbc.update("INSERT INTO extra_acceptance(extra_id,revision,actor_id,evidence_id) VALUES (?,?,?,?)",id,e.currentRevision(),actor.id(),input.evidenceId());
            jdbc.update("UPDATE extra_request SET version=version+1 WHERE id=?",id);
            audit.record(actor.id(),"EXTRA_ACCEPTED",p,Map.of("extraId",id,"revision",e.currentRevision(),"evidenceId",input.evidenceId(),"reason",input.reason(),"mode",actor.manager()?"DOCUMENTED_EXTERNAL":"AUTHENTICATED_REPRESENTATIVE"));return extra(p,id,false);
        });
    }
    @Transactional
    public Extra approve(Actor actor,UUID p,UUID id,String key,String etag,Evidence input){
        actor.requireManager();sensitive.require(actor);
        return commands.execute(actor.id(),"POST /projects/"+p+"/extras/"+id+"/approve",key,List.of(input,Objects.toString(etag,"")),Extra.class,()->{
            projects.writable(projects.lock(actor,p));Extra e=extra(p,id,true);Versions.check(etag,e.version());Contract c=load(p);
            if(!c.state().equals("FORMALIZED")||!e.state().equals("PENDIENTE_APROBACION"))Versions.conflict("La aprobación requiere contrato formalizado y extra pendiente.");
            ExtraRevision r=e.revisions().getLast();if(r.acceptanceEvidence()==null)Versions.conflict("Registre la aceptación de esta revisión.");
            documents.requireEvidence(actor,p,r.acceptanceEvidence());documents.requireEvidence(actor,p,input.evidenceId());
            addAmendment(actor,p,id,r.revision(),"EXTRA",r.net().decimal(),r.tax().decimal(),r.daysDelta(),input.evidenceId(),input.reason(),null);
            jdbc.update("UPDATE extra_request SET state='APROBADO',version=version+1 WHERE id=?",id);events.publishEvent(new ScopeChanged(actor,p));return extra(p,id,false);
        });
    }
    private void addAmendment(Actor actor,UUID p,UUID extra,Integer revision,String kind,BigDecimal net,BigDecimal tax,int days,UUID evidence,String reason,UUID reversal){
        Contract c=load(p);
        if(c.currentTotal().decimal().add(net).add(tax).compareTo(new BigDecimal("999999999999999999.99"))>0)Versions.invalid("El total contractual excede el rango admitido.");
        if(c.currentTotal().decimal().add(net).add(tax).signum()<0||c.startsOn().isAfter(c.currentEndsOn().plusDays(days)))Versions.invalid("La enmienda dejaría monto o plazo contractual inválido.");
        UUID id=UUID.randomUUID();documents.retain(actor,p,evidence,"AMENDMENT_"+id);
        jdbc.update("INSERT INTO contract_amendment(id,project_id,extra_id,revision,kind,net_delta,tax_delta,days_delta,evidence_id,reason,reversal_of,actor_id) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",id,p,extra,revision,kind,net,tax,days,evidence,reason,reversal,actor.id());
        audit.record(actor.id(),"CONTRACT_AMENDMENT_"+kind,p,Map.of("amendmentId",id,"evidenceId",evidence,"reason",reason,"previousTotal",c.currentTotal().amount(),"currentTotal",money(c.currentTotal().decimal().add(net).add(tax)).amount()));
    }
    @Transactional
    public Contract adjust(Actor actor,UUID p,String key,String etag,Adjustment input){
        actor.requireManager();sensitive.require(actor);BigDecimal net=decimal(input.netDelta(),true),tax=decimal(input.taxDelta(),true);
        return commands.execute(actor.id(),"POST /projects/"+p+"/contract/amendments",key,List.of(input,Objects.toString(etag,"")),Contract.class,()->{
            var project=projects.lock(actor,p);projects.writable(project);Versions.check(etag,load(p).version());
            if(!load(p).state().equals("FORMALIZED"))Versions.conflict("Formalice primero el contrato.");
            documents.requireEvidence(actor,p,input.evidenceId());addAmendment(actor,p,null,null,"ADJUSTMENT",net,tax,input.daysDelta(),input.evidenceId(),input.reason(),null);
            jdbc.update("UPDATE project SET version=version+1 WHERE id=?",p);events.publishEvent(new ScopeChanged(actor,p));return load(p);
        });
    }
    @Transactional
    public Extra cancel(Actor actor,UUID p,UUID id,String key,String etag,Adjustment input){
        actor.requireManager();sensitive.require(actor);BigDecimal net=decimal(input.netDelta(),true),tax=decimal(input.taxDelta(),true);
        return commands.execute(actor.id(),"POST /projects/"+p+"/extras/"+id+"/cancel",key,List.of(input,Objects.toString(etag,"")),Extra.class,()->{
            projects.writable(projects.lock(actor,p));Extra e=extra(p,id,true);Versions.check(etag,e.version());
            if(Set.of("CANCELADO","RECHAZADO","TERMINADO").contains(e.state()))Versions.conflict("El extra no puede cancelarse.");
            documents.requireEvidence(actor,p,input.evidenceId());
            if(Set.of("APROBADO","EN_EJECUCION").contains(e.state())){
                ExtraRevision r=e.revisions().getLast();
                if(net.signum()>0||tax.signum()>0||net.abs().compareTo(r.net().decimal())>0||tax.abs().compareTo(r.tax().decimal())>0)Versions.invalid("La reducción no puede exceder la revisión aprobada.");
                UUID original=jdbc.queryForObject("SELECT id FROM contract_amendment WHERE extra_id=? AND kind='EXTRA' AND revision=?",UUID.class,id,e.currentRevision());
                addAmendment(actor,p,id,e.currentRevision(),"CANCELLATION",net,tax,input.daysDelta(),input.evidenceId(),input.reason(),original);
            }else if(net.signum()!=0||tax.signum()!=0||input.daysDelta()!=0)Versions.invalid("Un extra pendiente no tiene importe contractual que revertir.");
            jdbc.update("UPDATE extra_request SET state='CANCELADO',version=version+1 WHERE id=?",id);
            events.publishEvent(new ScopeChanged(actor,p));
            audit.record(actor.id(),"EXTRA_CANCELLED",p,Map.of("extraId",id,"reason",input.reason(),"evidenceId",input.evidenceId()));return extra(p,id,false);
        });
    }
    @Transactional
    public Extra progress(Actor actor,UUID p,UUID id,String key,String etag,String state){
        projects.operations(actor,p);
        return commands.execute(actor.id(),"POST /projects/"+p+"/extras/"+id+"/progress",key,List.of(state,Objects.toString(etag,"")),Extra.class,()->{
            projects.writable(projects.lock(actor,p));Extra e=extra(p,id,true);Versions.check(etag,e.version());
            if(!(e.state().equals("APROBADO")&&state.equals("EN_EJECUCION")||e.state().equals("EN_EJECUCION")&&state.equals("TERMINADO")))Versions.conflict("Transición de ejecución inválida.");
            jdbc.update("UPDATE extra_request SET state=?,version=version+1 WHERE id=?",state,id);audit.record(actor.id(),"EXTRA_"+state,p,Map.of("extraId",id));return extra(p,id,false);
        });
    }
}
