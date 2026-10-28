package com.daraya.atoconstrucciones.parties;
import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.identity.AccountDirectory;
import com.daraya.atoconstrucciones.identity.SensitiveAuthenticationPolicy;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.constraints.*;
import com.fasterxml.jackson.annotation.JsonInclude;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class PartyService {
    public record Input(@NotBlank @Size(max=160) String name,@NotBlank @Email @Size(max=254) String email,
                        @NotBlank @Pattern(regexp="(?=(?:[^0-9]*[0-9]){7})[+0-9 ()-]{7,24}") String phone,@NotNull @Size(max=100) String category,boolean active,
                        SupplierCategory supplierCategory) {
        public Input(String name,String email,String phone,String category,boolean active){this(name,email,phone,category,active,null);}
    }
    public record Member(@NotNull UUID userId,boolean active) {}
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Party(UUID id,String kind,String name,String email,String phone,String category,boolean active,long version,SupplierCategory supplierCategory) {}
    public record Page(List<Party> items,int page,int size) {}
    public record MemberAccount(AccountDirectory.Account account,boolean active) {}
    public record MemberPage(List<MemberAccount> items,int page,int size,boolean hasMore) {}
    private final JdbcTemplate jdbc;private final AuditService audit;private final IdempotencyService commands;
    private final SensitiveAuthenticationPolicy sensitive;
    private final AccountDirectory accounts;
    public PartyService(JdbcTemplate jdbc,AuditService audit,IdempotencyService commands,SensitiveAuthenticationPolicy sensitive,AccountDirectory accounts){this.sensitive=sensitive;this.jdbc=jdbc;this.audit=audit;this.commands=commands;this.accounts=accounts;}
    private Party load(UUID id,String kind,boolean lock) {
        var rows=jdbc.query("SELECT * FROM party WHERE id=? AND kind=?"+(lock?" FOR UPDATE":""),(r,n)->new Party(r.getObject("id",UUID.class),r.getString("kind"),r.getString("name"),r.getString("email"),r.getString("phone"),r.getString("category"),r.getBoolean("active"),r.getLong("version"),r.getString("supplier_category")==null?null:SupplierCategory.valueOf(r.getString("supplier_category"))),id,kind);
        if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    private boolean operations(Actor actor,UUID id) {
        return actor.roles().contains("ASISTENTE_ADMINISTRATIVA")&&Boolean.TRUE.equals(jdbc.queryForObject("""
          SELECT EXISTS(SELECT 1 FROM project p JOIN project_member m ON m.project_id=p.id WHERE m.user_id=? AND m.capacity='OPERATIONS' AND m.active
            AND (p.client_id=? OR EXISTS(SELECT 1 FROM project_member s WHERE s.project_id=p.id AND s.supplier_id=? AND s.active)))
          """,Boolean.class,actor.id(),id,id));
    }
    public Party read(Actor actor,UUID id,String kind) {
        if(actor.manager()||operations(actor,id))return load(id,kind,false);
        if(!actor.roles().contains(kind.equals("CLIENT")?"CLIENTE":"PROVEEDOR")||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM party_member WHERE party_id=? AND user_id=? AND active)",Boolean.class,id,actor.id())))throw ApiException.missing();
        Party p=load(id,kind,false);return new Party(p.id(),p.kind(),p.name(),null,null,null,p.active(),p.version(),null);
    }
    public Page list(Actor actor,String kind,int page,int size) {
        return list(actor,kind,page,size,null);
    }
    public Page list(Actor actor,String kind,int page,int size,SupplierCategory category) {
        if(category!=null&&!kind.equals("SUPPLIER"))Versions.invalid("Las categorías de proveedor se aplican solo a proveedores.");
        if(page<0||page>100000||size<1||size>100)throw new ApiException(400,"INVALID_PAGINATION","Paginación inválida.");
        var ids=jdbc.query("""
            SELECT p.id FROM party p WHERE p.kind=? AND (? OR p.supplier_category=?) AND (? OR
            (? AND EXISTS(SELECT 1 FROM party_member m WHERE m.party_id=p.id AND m.user_id=? AND m.active)) OR
            (? AND EXISTS(SELECT 1 FROM project pr JOIN project_member m ON m.project_id=pr.id WHERE m.user_id=? AND m.capacity='OPERATIONS' AND m.active
            AND (pr.client_id=p.id OR EXISTS(SELECT 1 FROM project_member s WHERE s.project_id=pr.id AND s.supplier_id=p.id AND s.active))))) ORDER BY p.id LIMIT ? OFFSET ?
            """,(r,n)->r.getObject(1,UUID.class),kind,category==null,category==null?"":category.name(),actor.manager(),actor.roles().contains(kind.equals("CLIENT")?"CLIENTE":"PROVEEDOR"),actor.id(),actor.roles().contains("ASISTENTE_ADMINISTRATIVA"),actor.id(),size,page*size);
        return new Page(ids.stream().map(id->read(actor,id,kind)).toList(),page,size);
    }
    @Transactional
    public Party create(Actor actor,String kind,String key,Input input) {
        actor.requireManager();return commands.execute(actor.id(),"POST /parties/"+kind,key,input,Party.class,()->{
            SupplierCategory category=category(kind,input.supplierCategory(),null);
            UUID id=UUID.randomUUID();jdbc.update("INSERT INTO party(id,kind,name,email,phone,category,active,created_by,supplier_category) VALUES (?,?,?,?,?,?,?,?,?)",id,kind,input.name().trim(),input.email().trim().toLowerCase(Locale.ROOT),input.phone().trim(),input.category().trim(),input.active(),actor.id(),category==null?null:category.name());
            audit.record(actor.id(),"PARTY_CREATED",id);return load(id,kind,false);
        });
    }
    @Transactional
    public Party edit(Actor actor,UUID id,String kind,String key,String etag,Input input) {
        read(actor,id,kind);if(!actor.manager()&&!operations(actor,id))throw ApiException.forbidden();
        return commands.execute(actor.id(),"PATCH /parties/"+id,key,List.of(input,Objects.toString(etag,"")),Party.class,()->{
            Party p=load(id,kind,true);Versions.check(etag,p.version());
            if(!actor.manager()&&p.active()!=input.active())throw ApiException.forbidden();
            if(!actor.manager()&&!p.name().equals(input.name().trim()))throw ApiException.forbidden();
            SupplierCategory category=category(kind,input.supplierCategory(),p.supplierCategory());
            jdbc.update("UPDATE party SET name=?,email=?,phone=?,category=?,active=?,supplier_category=?,version=version+1 WHERE id=?",input.name().trim(),input.email().trim().toLowerCase(Locale.ROOT),input.phone().trim(),input.category().trim(),input.active(),category==null?null:category.name(),id);
            audit.record(actor.id(),"PARTY_EDITED",id);return load(id,kind,false);
        });
    }
    private SupplierCategory category(String kind,SupplierCategory input,SupplierCategory previous){
        if(kind.equals("CLIENT")){if(input!=null)Versions.invalid("Un cliente no recibe categoría de proveedor.");return null;}
        return input!=null?input:previous!=null?previous:SupplierCategory.OTHER;
    }
    @Transactional
    public Party member(Actor actor,UUID id,String kind,String key,String etag,Member input) {
        actor.requireManager();sensitive.require(actor);
        return commands.execute(actor.id(),"PUT /parties/"+id+"/members",key,List.of(input,Objects.toString(etag,"")),Party.class,()->{
            Party p=load(id,kind,true);Versions.check(etag,p.version());
            if(input.active()&&!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_account u JOIN user_role r ON r.user_id=u.id WHERE u.id=? AND u.enabled AND r.role=?)",Boolean.class,input.userId(),kind.equals("CLIENT")?"CLIENTE":"PROVEEDOR")))Versions.invalid("La cuenta no tiene el rol activo requerido.");
            jdbc.update("INSERT INTO party_member(party_id,user_id,active) VALUES (?,?,?) ON CONFLICT(party_id,user_id) DO UPDATE SET active=excluded.active",id,input.userId(),input.active());
            jdbc.update("UPDATE party SET version=version+1 WHERE id=?",id);audit.record(actor.id(),input.active()?"PARTY_MEMBER_GRANTED":"PARTY_MEMBER_REVOKED",id);return load(id,kind,false);
        });
    }
    public List<Member> members(Actor actor,UUID id,String kind){actor.requireManager();load(id,kind,false);return jdbc.query("SELECT user_id,active FROM party_member WHERE party_id=? ORDER BY user_id",(r,n)->new Member(r.getObject(1,UUID.class),r.getBoolean(2)),id);}
    public MemberPage memberAccounts(Actor actor,UUID id,String kind,int page,int size){
        actor.requireManager();load(id,kind,false);AccountDirectory.pagination(page,size);
        var rows=jdbc.query("SELECT user_id,active FROM party_member WHERE party_id=? ORDER BY user_id LIMIT ? OFFSET ?",(r,n)->new Member(r.getObject(1,UUID.class),r.getBoolean(2)),id,size+1,page*size);
        var names=accounts.describe(rows.stream().map(Member::userId).toList());
        return new MemberPage(rows.stream().limit(size).map(m->new MemberAccount(names.get(m.userId()),m.active())).toList(),page,size,rows.size()>size);
    }
}
