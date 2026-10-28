package com.daraya.atoconstrucciones.projects;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.identity.AccountDirectory;
import com.daraya.atoconstrucciones.identity.SensitiveAuthenticationPolicy;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;

@Service
public class ProjectService {
    public record PlanningChanged(Actor actor,UUID projectId) {}
    public record Input(@NotBlank @Pattern(regexp="[A-Z0-9-]{3,30}") String code,
        @NotBlank @Size(max=160) String name, @NotNull UUID clientId,
        @NotNull @Size(max=500) String location, BigDecimal latitude, BigDecimal longitude,
        @NotBlank @Email @Size(max=254) String contactEmail, @NotNull LocalDate startsOn, @NotNull LocalDate estimatedEndsOn) {}
    public record Edit(@NotBlank @Size(max=160) String name, @NotNull @Size(max=500) String location,
        BigDecimal latitude, BigDecimal longitude, @NotBlank @Email @Size(max=254) String contactEmail,
        @NotNull LocalDate startsOn, @NotNull LocalDate estimatedEndsOn) {}
    public record Member(@NotNull UUID userId, @Pattern(regexp="CLIENT|OPERATIONS|SUPPLIER") @NotNull String capacity,
                         UUID supplierId, boolean representative, boolean active) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Project(UUID id, String code, String name, UUID clientId, String location, BigDecimal latitude,
                          BigDecimal longitude, String contactEmail, LocalDate startsOn, LocalDate estimatedEndsOn, String state, long version) {}
    public record Page(List<Project> items, int page, int size) {}
    public record MemberAccount(AccountDirectory.Account account,String capacity,UUID supplierId,String supplierName,boolean representative,boolean active) {}
    public record MemberPage(List<MemberAccount> items,int page,int size,boolean hasMore) {}
    private record NamedMember(Member member,String supplierName) {}
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final IdempotencyService commands;
    private final SensitiveAuthenticationPolicy sensitive;
    private final ApplicationEventPublisher events;
    private final AccountDirectory accounts;
    public ProjectService(JdbcTemplate jdbc, AuditService audit, IdempotencyService commands,SensitiveAuthenticationPolicy sensitive,ApplicationEventPublisher events,AccountDirectory accounts) {this.sensitive=sensitive;this.events=events;this.accounts=accounts;
        this.jdbc=jdbc; this.audit=audit; this.commands=commands;
    }
    private Project load(UUID id, boolean lock) {
        var rows=jdbc.query("SELECT * FROM project WHERE id=?"+(lock?" FOR UPDATE":""), (r,n)->new Project(
            r.getObject("id",UUID.class),r.getString("code"),r.getString("name"),r.getObject("client_id",UUID.class),
            r.getString("location"),r.getBigDecimal("latitude"),r.getBigDecimal("longitude"),r.getString("contact_email"),
            r.getObject("starts_on",LocalDate.class),r.getObject("estimated_ends_on",LocalDate.class),r.getString("state"),r.getLong("version")),id);
        if(rows.isEmpty()) throw ApiException.missing(); return rows.getFirst();
    }
    public boolean capacity(Actor actor, UUID id, String capacity) {
        String role=switch(capacity){case "CLIENT"->"CLIENTE";case "OPERATIONS"->"ASISTENTE_ADMINISTRATIVA";case "SUPPLIER"->"PROVEEDOR";default->"";};
        if(!actor.roles().contains(role)) return false;
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM project_member m JOIN project p ON p.id=m.project_id
              WHERE m.project_id=? AND m.user_id=? AND m.capacity=? AND m.active
              AND (m.capacity='OPERATIONS' OR EXISTS(SELECT 1 FROM party_member pm WHERE pm.user_id=m.user_id AND pm.active
                 AND pm.party_id=CASE WHEN m.capacity='CLIENT' THEN p.client_id ELSE m.supplier_id END)))
            """,Boolean.class,id,actor.id(),capacity));
    }
    public void authorize(Actor actor, UUID id) {
        if(!actor.manager() && !capacity(actor,id,"CLIENT") && !capacity(actor,id,"OPERATIONS") && !capacity(actor,id,"SUPPLIER")) throw ApiException.missing();
    }
    public void operations(Actor actor, UUID id) {
        authorize(actor,id);
        if(!actor.manager()&&!capacity(actor,id,"OPERATIONS")) throw ApiException.forbidden();
    }
    public List<UUID> assignedSuppliers(Actor actor,UUID id) {
        authorize(actor,id);
        return jdbc.queryForList("SELECT DISTINCT m.supplier_id FROM project_member m JOIN party p ON p.id=m.supplier_id AND p.active JOIN party_member pm ON pm.party_id=p.id AND pm.user_id=m.user_id AND pm.active JOIN user_account u ON u.id=m.user_id AND u.enabled JOIN user_role r ON r.user_id=u.id AND r.role='PROVEEDOR' WHERE m.project_id=? AND m.capacity='SUPPLIER' AND m.active ORDER BY m.supplier_id",UUID.class,id);
    }
    public boolean supplierAssigned(Actor actor,UUID id,Collection<UUID> suppliers) {
        if(!capacity(actor,id,"SUPPLIER")||suppliers.isEmpty())return false;
        return jdbc.queryForList("SELECT m.supplier_id FROM project_member m JOIN party p ON p.id=m.supplier_id AND p.active WHERE m.project_id=? AND m.user_id=? AND m.capacity='SUPPLIER' AND m.active",UUID.class,id,actor.id()).stream().anyMatch(suppliers::contains);
    }
    public void representative(Actor actor, UUID id) {
        authorize(actor,id);
        if(!capacity(actor,id,"CLIENT") || !Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM project_member WHERE project_id=? AND user_id=? AND capacity='CLIENT' AND active AND representative)",Boolean.class,id,actor.id()))) throw ApiException.forbidden();
    }
    @Transactional
    public Project lock(Actor actor, UUID id) { authorize(actor,id);return load(id,true); }
    public void writable(Project project) {
        if(Set.of("CANCELADO","CERRADO","EN_CIERRE").contains(project.state())) Versions.conflict("La obra no admite cambios ordinarios.");
    }
    public Project read(Actor actor, UUID id) {
        authorize(actor,id); Project p=load(id,false);
        if(actor.manager()||capacity(actor,id,"OPERATIONS"))return p;
        if(capacity(actor,id,"CLIENT"))return p;
        return new Project(p.id(),p.code(),p.name(),null,p.location(),p.latitude(),p.longitude(),null,p.startsOn(),p.estimatedEndsOn(),p.state(),p.version());
    }
    public Page list(Actor actor,int page,int size) {
        if(page<0||page>100000||size<1||size>100)throw new ApiException(400,"INVALID_PAGINATION","Paginación inválida.");
        // Filtrar antes de paginar; el join incluye la vigencia de la membresía de la parte.
        var ids=jdbc.query("""
          SELECT p.id FROM project p WHERE ? OR EXISTS(SELECT 1 FROM project_member m WHERE m.project_id=p.id AND m.user_id=? AND m.active
          AND ((m.capacity='OPERATIONS' AND ?) OR (m.capacity='CLIENT' AND ? AND EXISTS(SELECT 1 FROM party_member pm WHERE pm.party_id=p.client_id AND pm.user_id=m.user_id AND pm.active))
          OR (m.capacity='SUPPLIER' AND ? AND EXISTS(SELECT 1 FROM party_member pm WHERE pm.party_id=m.supplier_id AND pm.user_id=m.user_id AND pm.active)))) ORDER BY p.id LIMIT ? OFFSET ?
          """,(r,n)->r.getObject(1,UUID.class),actor.manager(),actor.id(),actor.roles().contains("ASISTENTE_ADMINISTRATIVA"),actor.roles().contains("CLIENTE"),actor.roles().contains("PROVEEDOR"),size,page*size);
        return new Page(ids.stream().map(id->read(actor,id)).toList(),page,size);
    }
    private void validate(LocalDate start,LocalDate end,BigDecimal lat,BigDecimal lon) {
        if(start==null||end==null||start.isAfter(end))Versions.invalid("Las fechas de la obra son inválidas.");
        if((lat==null)!=(lon==null)||lat!=null&&(lat.abs().compareTo(new BigDecimal("90"))>0||lon.abs().compareTo(new BigDecimal("180"))>0))Versions.invalid("Coordenadas inválidas.");
    }
    public Page clientList(Actor actor,int page,int size) {
        if(!actor.roles().contains("CLIENTE"))throw ApiException.forbidden();
        if(page<0||page>100000||size<1||size>100)throw new ApiException(400,"INVALID_PAGINATION","Paginación inválida.");
        var ids=jdbc.queryForList("SELECT p.id FROM project p JOIN project_member m ON m.project_id=p.id AND m.capacity='CLIENT' AND m.active JOIN party_member pm ON pm.party_id=p.client_id AND pm.user_id=m.user_id AND pm.active WHERE m.user_id=? ORDER BY p.id LIMIT ? OFFSET ?",UUID.class,actor.id(),size,page*size);
        return new Page(ids.stream().map(id->read(actor,id)).toList(),page,size);
    }
    @Transactional
    public Project create(Actor actor,String key,Input input) {
        if(!actor.manager()) {
            if(!actor.roles().contains("ASISTENTE_ADMINISTRATIVA"))throw ApiException.forbidden();
            if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM project p JOIN project_member m ON m.project_id=p.id WHERE p.client_id=? AND m.user_id=? AND m.capacity='OPERATIONS' AND m.active)",Boolean.class,input.clientId(),actor.id())))throw ApiException.missing();
        }
        validate(input.startsOn(),input.estimatedEndsOn(),input.latitude(),input.longitude());
        return commands.execute(actor.id(),"POST /projects",key,input,Project.class,()->{
            var clients=jdbc.queryForList("SELECT id FROM party WHERE id=? AND kind='CLIENT' AND active FOR UPDATE",UUID.class,input.clientId());
            if(clients.isEmpty())Versions.invalid("Seleccione un cliente activo.");
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM project WHERE code=?)",Boolean.class,input.code())))throw new ApiException(409,"DUPLICATE_CODE","El código ya existe.");
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO project(id,code,name,client_id,location,latitude,longitude,contact_email,starts_on,estimated_ends_on,created_by) VALUES (?,?,?,?,?,?,?,?,?,?,?)",
                id,input.code(),input.name().trim(),input.clientId(),input.location().trim(),input.latitude(),input.longitude(),input.contactEmail().trim().toLowerCase(Locale.ROOT),input.startsOn(),input.estimatedEndsOn(),actor.id());
            if(!actor.manager())jdbc.update("INSERT INTO project_member(project_id,user_id,capacity,active) VALUES (?,?,'OPERATIONS',true)",id,actor.id());
            audit.record(actor.id(),"PROJECT_CREATED",id);return load(id,false);
        });
    }
    @Transactional
    public Project edit(Actor actor,UUID id,String key,String etag,Edit input) {
        operations(actor,id);validate(input.startsOn(),input.estimatedEndsOn(),input.latitude(),input.longitude());
        return commands.execute(actor.id(),"PATCH /projects/"+id,key,List.of(input,Objects.toString(etag,"")),Project.class,()->{
            Project p=lock(actor,id);writable(p);Versions.check(etag,p.version());
            jdbc.update("UPDATE project SET name=?,location=?,latitude=?,longitude=?,contact_email=?,starts_on=?,estimated_ends_on=?,version=version+1 WHERE id=?",
                input.name().trim(),input.location().trim(),input.latitude(),input.longitude(),input.contactEmail().trim().toLowerCase(Locale.ROOT),input.startsOn(),input.estimatedEndsOn(),id);
            audit.record(actor.id(),"PROJECT_EDITED",id);events.publishEvent(new PlanningChanged(actor,id));return load(id,false);
        });
    }
    @Transactional
    public Project member(Actor actor,UUID id,String key,String etag,Member input) {
        actor.requireManager();sensitive.require(actor);
        return commands.execute(actor.id(),"PUT /projects/"+id+"/members",key,List.of(input,Objects.toString(etag,"")),Project.class,()->{
            Project p=lock(actor,id);writable(p);Versions.check(etag,p.version());
            String role=switch(input.capacity()){case "CLIENT"->"CLIENTE";case "OPERATIONS"->"ASISTENTE_ADMINISTRATIVA";case "SUPPLIER"->"PROVEEDOR";default->throw new ApiException(422,"VALIDATION_FAILED","Capacidad inválida.");};
            if((input.supplierId()!=null)!=input.capacity().equals("SUPPLIER")||input.representative()&&!input.capacity().equals("CLIENT"))Versions.invalid("Membresía incompatible.");
            if(input.active()) {
                if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_account u JOIN user_role r ON r.user_id=u.id WHERE u.id=? AND u.enabled AND r.role=?)",Boolean.class,input.userId(),role)))Versions.invalid("La cuenta no tiene el rol activo requerido.");
                UUID party=input.capacity().equals("CLIENT")?p.clientId():input.supplierId();
                if(party!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM party_member pm JOIN party p ON p.id=pm.party_id WHERE pm.party_id=? AND pm.user_id=? AND pm.active AND p.active AND p.kind=?)",Boolean.class,party,input.userId(),input.capacity())))Versions.invalid("La cuenta debe pertenecer a la parte activa.");
            }
            jdbc.update("INSERT INTO project_member(project_id,user_id,capacity,supplier_id,representative,active) VALUES (?,?,?,?,?,?) ON CONFLICT(project_id,user_id,capacity) DO UPDATE SET supplier_id=excluded.supplier_id,representative=excluded.representative,active=excluded.active",id,input.userId(),input.capacity(),input.supplierId(),input.representative(),input.active());
            jdbc.update("UPDATE project SET version=version+1 WHERE id=?",id);
            audit.record(actor.id(),input.active()?"PROJECT_MEMBER_GRANTED":"PROJECT_MEMBER_REVOKED",id);return load(id,false);
        });
    }
    public List<Member> members(Actor actor,UUID id) {
        operations(actor,id);return jdbc.query("SELECT * FROM project_member WHERE project_id=? ORDER BY user_id,capacity",(r,n)->new Member(r.getObject("user_id",UUID.class),r.getString("capacity"),r.getObject("supplier_id",UUID.class),r.getBoolean("representative"),r.getBoolean("active")),id);
    }
    public MemberPage memberAccounts(Actor actor,UUID id,int page,int size){
        actor.requireManager();load(id,false);AccountDirectory.pagination(page,size);
        var rows=jdbc.query("SELECT m.*,p.name AS supplier_name FROM project_member m LEFT JOIN party p ON p.id=m.supplier_id WHERE m.project_id=? ORDER BY m.user_id,m.capacity LIMIT ? OFFSET ?",
            (r,n)->new NamedMember(new Member(r.getObject("user_id",UUID.class),r.getString("capacity"),r.getObject("supplier_id",UUID.class),r.getBoolean("representative"),r.getBoolean("active")),r.getString("supplier_name")),id,size+1,page*size);
        var names=accounts.describe(rows.stream().map(m->m.member().userId()).toList());
        return new MemberPage(rows.stream().limit(size).map(m->new MemberAccount(names.get(m.member().userId()),m.member().capacity(),m.member().supplierId(),m.supplierName(),m.member().representative(),m.member().active())).toList(),page,size,rows.size()>size);
    }
    @Transactional public AccountDirectory.Page accountCandidates(Actor actor,UUID id,String query,String capacity,UUID supplierId,int page,int size){
        actor.requireManager();Project p=load(id,false);
        String role=switch(capacity){case "CLIENT"->"CLIENTE";case "OPERATIONS"->"ASISTENTE_ADMINISTRATIVA";case "SUPPLIER"->"PROVEEDOR";default->throw new ApiException(400,"INVALID_CAPACITY","Capacidad inválida.");};
        if((supplierId!=null)!=capacity.equals("SUPPLIER"))Versions.invalid("Seleccione el proveedor solo para acceso de proveedor.");
        UUID party=capacity.equals("CLIENT")?p.clientId():supplierId;
        if(party!=null&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM party WHERE id=? AND kind=? AND active)",Boolean.class,party,capacity)))Versions.invalid("Seleccione una parte activa.");
        return accounts.searchForParty(actor,query,role,page,size,party);
    }
    @Transactional
    public Project transition(Actor actor,UUID id,String key,String etag,String action,String reason) {
        actor.requireManager();sensitive.require(actor);
        return commands.execute(actor.id(),"POST /projects/"+id+"/"+action,key,List.of(Objects.toString(etag,""),reason),Project.class,()->{
            Project p=lock(actor,id);Versions.check(etag,p.version());writable(p);
            String next=switch(action){
                case "activate"->{
                    if(!Set.of("BORRADOR","SUSPENDIDO").contains(p.state()))Versions.conflict("La obra no puede activarse desde este estado.");
                    if(p.location().isBlank())Versions.invalid("Indique la ubicación antes de activar.");
                    if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM contract c WHERE c.project_id=? AND c.state='FORMALIZED')",Boolean.class,id)))Versions.conflict("Formalice el contrato base antes de activar.");
                    if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM project_member m JOIN party_member pm ON pm.user_id=m.user_id AND pm.party_id=? JOIN user_account u ON u.id=m.user_id JOIN user_role r ON r.user_id=u.id AND r.role='CLIENTE' WHERE m.project_id=? AND m.capacity='CLIENT' AND m.representative AND m.active AND pm.active AND u.enabled)",Boolean.class,p.clientId(),id)))Versions.conflict("Asigne un representante autorizado del cliente.");
                    yield "ACTIVO";
                }
                case "suspend"->{if(!p.state().equals("ACTIVO"))Versions.conflict("Solo una obra activa puede suspenderse.");yield "SUSPENDIDO";}
                case "cancel"->{
                    // La liquidación financiera de obras formalizadas depende de M2/M5.
                    if(!p.state().equals("BORRADOR")||Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM contract WHERE project_id=? AND state='FORMALIZED')",Boolean.class,id)))Versions.conflict("La cancelación contractual requiere liquidación y cierre en un hito posterior.");
                    if(reason.isBlank())Versions.invalid("Indique el motivo de cancelación.");yield "CANCELADO";
                }
                default->throw ApiException.missing();
            };
            jdbc.update("UPDATE project SET state=?,version=version+1 WHERE id=?",next,id);
            audit.record(actor.id(),"PROJECT_"+next,id,Map.of("reason",reason,"fromVersion",p.version(),"toVersion",p.version()+1));return load(id,false);
        });
    }
}
