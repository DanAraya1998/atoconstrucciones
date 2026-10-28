package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.shared.ApiException;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Directorio privado de cuentas ya registradas; no consulta el directorio del IdP. */
@Service
public class AccountDirectory {
    public record Account(UUID id,String displayName,String email,Set<String> roles,boolean enabled) {}
    public record Page(List<Account> items,int page,int size,boolean hasMore) {}
    public record Search(@NotNull @Size(max=254)String query,String role,@Min(0) @Max(100000)Integer page,@Min(1) @Max(50)Integer size){public int pageValue(){return page==null?0:page;}public int sizeValue(){return size==null?20:size;}}
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    public AccountDirectory(JdbcTemplate jdbc,AuditService audit){this.jdbc=jdbc;this.audit=audit;}
    private static final String SELECT="""
        SELECT u.id,u.display_name,u.email,u.enabled,
        coalesce((SELECT string_agg(r.role,',' ORDER BY r.role) FROM user_role r WHERE r.user_id=u.id),'') AS roles
        FROM user_account u
        """;
    private final RowMapper<Account> mapper=(r,n)->new Account(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("email"),
        r.getString("roles").isEmpty()?Set.of():Set.of(r.getString("roles").split(",")),r.getBoolean("enabled"));
    public static void pagination(int page,int size){if(page<0||page>100000||size<1||size>50)throw new ApiException(400,"INVALID_PAGINATION","Paginación inválida.");}
    @Transactional public Page search(Actor actor,String query,String role,int page,int size){return searchForParty(actor,query,role,page,size,null);}
    @Transactional public Page searchForParty(Actor actor,String query,String role,int page,int size,UUID partyId){
        actor.requireManager();pagination(page,size);
        if(query==null||query.strip().length()<2||query.strip().length()>254)throw new ApiException(400,"INVALID_ACCOUNT_SEARCH","Busca por nombre o correo con entre 2 y 254 caracteres.");
        if(role!=null&&!Set.of("GERENTE","ADMIN","CLIENTE","PROVEEDOR","ASISTENTE_ADMINISTRATIVA").contains(role))throw new ApiException(400,"INVALID_ROLE","Rol inválido.");
        String pattern="%"+query.strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var rows=jdbc.query(SELECT+"""
            WHERE u.enabled AND u.access_state='APPROVED' AND u.email_verified AND (lower(u.display_name) LIKE ? ESCAPE '!' OR lower(u.email) LIKE ? ESCAPE '!')
            AND (?::text IS NULL OR EXISTS(SELECT 1 FROM user_role r WHERE r.user_id=u.id AND r.role=?))
            AND (?::uuid IS NULL OR EXISTS(SELECT 1 FROM party_member m JOIN party p ON p.id=m.party_id
                WHERE m.party_id=? AND m.user_id=u.id AND m.active AND p.active))
            ORDER BY lower(coalesce(nullif(u.display_name,''),u.email)),u.id LIMIT ? OFFSET ?
            """,mapper,pattern,pattern,role,role,partyId,partyId,size+1,page*size);
        audit.record(actor.id(),"ACCOUNT_DIRECTORY_READ",UUID.fromString("00000000-0000-0000-0000-000000000005"),Map.of("page",page,"size",size,"role",Objects.toString(role,"ALL")));
        return new Page(rows.stream().limit(size).toList(),page,size,rows.size()>size);
    }
    /** Contrato interno: el módulo que lo llama autoriza previamente el recurso/membresías. */
    public Map<UUID,Account> describe(Collection<UUID> ids){
        if(ids.isEmpty())return Map.of();
        var unique=new LinkedHashSet<>(ids);var result=new HashMap<UUID,Account>();
        jdbc.query(SELECT+" WHERE u.id IN ("+String.join(",",Collections.nCopies(unique.size(),"?"))+")",mapper,unique.toArray()).forEach(a->result.put(a.id(),a));
        return Map.copyOf(result);
    }
}
