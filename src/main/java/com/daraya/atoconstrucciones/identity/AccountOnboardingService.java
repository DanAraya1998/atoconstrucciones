package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.parties.PartyService;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;

@Service
public class AccountOnboardingService {
    public record ProjectGrant(@NotNull UUID projectId,@Min(1) long version,boolean representative) {}
    public record Access(@NotNull @Pattern(regexp="CLIENTE|PROVEEDOR|ASISTENTE_ADMINISTRATIVA") String role,
        UUID partyId,Long partyVersion,@NotNull @Size(max=100) List<@Valid ProjectGrant> projects) {}
    public record Direct(@NotBlank @Size(max=200) String displayName,@NotBlank @Email @Size(max=254) String email,@NotNull @Valid Access access) {}
    public record Decision(@NotBlank @Size(max=500) String reason) {}
    public record Search(@NotNull @Size(max=254) String query,@Pattern(regexp="ALL|PENDING|APPROVED|REJECTED|SUSPENDED") String state,
        @Min(0) @Max(100000) Integer page,@Min(1) @Max(50) Integer size) {}
    public record Account(UUID id,String displayName,String email,Set<String> roles,String state,boolean emailVerified,long version,String activationState,Instant createdAt) {}
    public record Page(List<Account> items,int page,int size,boolean hasMore) {}
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final IdempotencyService commands;
    private final SensitiveAuthenticationPolicy sensitive;
    private final PartyService parties;
    private final ProjectService projects;
    private final AccountProvisioningPort provisioning;
    private final JsonMapper json;
    public AccountOnboardingService(JdbcTemplate jdbc,AuditService audit,IdempotencyService commands,SensitiveAuthenticationPolicy sensitive,
        PartyService parties,ProjectService projects,AccountProvisioningPort provisioning,JsonMapper json){
        this.jdbc=jdbc;this.audit=audit;this.commands=commands;this.sensitive=sensitive;this.parties=parties;this.projects=projects;this.provisioning=provisioning;this.json=json;
    }
    private static final String SELECT="""
        SELECT u.*,coalesce(a.state,'NONE') AS activation_state,
        coalesce((SELECT string_agg(r.role,',' ORDER BY r.role) FROM user_role r WHERE r.user_id=u.id),'') AS roles
        FROM user_account u LEFT JOIN account_activation a ON a.user_id=u.id
        """;
    private Account map(java.sql.ResultSet r,int n)throws java.sql.SQLException{
        String roles=r.getString("roles");return new Account(r.getObject("id",UUID.class),r.getString("display_name"),r.getString("email"),
            roles.isEmpty()?Set.of():Set.of(roles.split(",")),r.getBoolean("enabled")?r.getString("access_state"):"SUSPENDED",r.getBoolean("email_verified"),r.getLong("access_version"),r.getString("activation_state"),r.getTimestamp("created_at").toInstant());
    }
    private Account load(UUID id,boolean lock){
        if(lock&&jdbc.queryForList("SELECT id FROM user_account WHERE id=? FOR UPDATE",UUID.class,id).isEmpty())throw ApiException.missing();
        var rows=jdbc.query(SELECT+" WHERE u.id=?",this::map,id);if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    private void manager(Actor actor){actor.requireManager();sensitive.require(actor);}
    private void editable(Account target){if(target.roles().contains("GERENTE"))throw ApiException.forbidden();}
    private String etag(long version){return "\""+version+"\"";}
    @Transactional
    public Page search(Actor actor,Search input){
        actor.requireManager();int page=input.page()==null?0:input.page(),size=input.size()==null?20:input.size();AccountDirectory.pagination(page,size);
        String state=Objects.requireNonNullElse(input.state(),"ALL");
        if(!Set.of("ALL","PENDING","APPROVED","REJECTED","SUSPENDED").contains(state))Versions.invalid("Estado inválido.");
        String pattern="%"+input.query().strip().toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";
        var rows=jdbc.query(SELECT+"""
            WHERE (lower(u.display_name) LIKE ? ESCAPE '!' OR lower(u.email) LIKE ? ESCAPE '!')
            AND (?='ALL' OR (?='SUSPENDED' AND NOT u.enabled) OR (u.enabled AND u.access_state=?))
            ORDER BY u.created_at DESC,u.id LIMIT ? OFFSET ?
            """,this::map,pattern,pattern,state,state,state,size+1,page*size);
        audit.record(actor.id(),"ACCOUNT_REVIEWS_READ",UUID.fromString("00000000-0000-0000-0000-000000000005"),Map.of("page",page,"size",size,"state",state));
        return new Page(rows.stream().limit(size).toList(),page,size,rows.size()>size);
    }
    public Account read(Actor actor,UUID id){actor.requireManager();return load(id,false);}
    public Account self(Actor actor){return load(actor.id(),false);}
    public boolean directAvailable(Actor actor){actor.requireManager();return provisioning.configured();}
    private void validateAccess(Actor actor,Access access){
        if(access==null||!Set.of("CLIENTE","PROVEEDOR","ASISTENTE_ADMINISTRATIVA").contains(access.role())||access.projects()==null||access.projects().size()>100)Versions.invalid("Seleccione un perfil operativo válido.");
        boolean operational=access.role().equals("ASISTENTE_ADMINISTRATIVA");
        if(operational&&(access.partyId()!=null||access.partyVersion()!=null)||!operational&&(access.partyId()==null||access.partyVersion()==null||access.partyVersion()<1))Versions.invalid("Seleccione el cliente o proveedor correspondiente.");
        if(!operational){
            var party=parties.read(actor,access.partyId(),access.role().equals("CLIENTE")?"CLIENT":"SUPPLIER");
            if(!party.active())Versions.invalid("Seleccione una parte activa.");Versions.check(etag(access.partyVersion()),party.version());
        }
        var seen=new HashSet<UUID>();
        // Orden fijo de locks para altas concurrentes en varias obras.
        for(var grant:access.projects().stream().sorted(Comparator.comparing(ProjectGrant::projectId)).toList()){
            if(grant.projectId()==null||!seen.add(grant.projectId()))Versions.invalid("Hay obras repetidas o inválidas.");
            var project=projects.lock(actor,grant.projectId());projects.writable(project);Versions.check(etag(grant.version()),project.version());
            if(access.role().equals("CLIENTE")&&!project.clientId().equals(access.partyId()))Versions.invalid("La obra pertenece a otro cliente.");
            if(grant.representative()&&!access.role().equals("CLIENTE"))Versions.invalid("Solo un cliente puede representar al contratante.");
        }
    }
    private void grant(Actor actor,UUID id,String key,Access access){
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?) ON CONFLICT DO NOTHING",id,access.role());
        if(access.partyId()!=null)parties.member(actor,access.partyId(),access.role().equals("CLIENTE")?"CLIENT":"SUPPLIER",key+":party",etag(access.partyVersion()),new PartyService.Member(id,true));
        String capacity=switch(access.role()){case "CLIENTE"->"CLIENT";case "PROVEEDOR"->"SUPPLIER";default->"OPERATIONS";};
        for(var project:access.projects().stream().sorted(Comparator.comparing(ProjectGrant::projectId)).toList())projects.member(actor,project.projectId(),key+":"+project.projectId(),etag(project.version()),
            new ProjectService.Member(id,capacity,capacity.equals("SUPPLIER")?access.partyId():null,project.representative(),true));
    }
    @Transactional
    public Account create(Actor actor,String key,Direct input){
        manager(actor);
        return commands.execute(actor.id(),"POST /accounts",key,input,Account.class,()->{
            String email=input.email().strip().toLowerCase(Locale.ROOT);
            // No se adopta ni fusiona una identidad por coincidencia de correo.
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,7331))",(r,n)->1,email);
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_account WHERE lower(email)=?)",Boolean.class,email)))throw new ApiException(409,"ACCOUNT_ALREADY_EXISTS","Ya existe una cuenta con ese correo. Búscala en Usuarios y accesos para revisar sus permisos.");
            validateAccess(actor,input.access());
            String reference=UUID.nameUUIDFromBytes((actor.id()+":"+key).getBytes(StandardCharsets.UTF_8)).toString();
            String fingerprint=IdempotencyService.hash(json.writeValueAsString(input));
            var remote=provisioning.create(reference,fingerprint,input.displayName().strip(),email);
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO user_account(id,issuer,subject,email,display_name,access_state,email_verified,decided_by,decided_at) VALUES (?,?,?,?,?,'APPROVED',false,?,now())",id,remote.issuer(),remote.subject(),email,input.displayName().strip(),actor.id());
            grant(actor,id,reference,input.access());
            jdbc.update("INSERT INTO account_activation(user_id) VALUES (?)",id);
            audit.record(actor.id(),"ACCOUNT_DIRECT_CREATED",id,Map.of("role",input.access().role(),"projects",input.access().projects().size()));
            return load(id,false);
        });
    }
    @Transactional
    public Account approve(Actor actor,UUID id,String key,String version,Access access){
        manager(actor);String childKey=IdempotencyService.hash(actor.id()+":"+id+":"+key);
        return commands.execute(actor.id(),"POST /accounts/"+id+"/approve",key,List.of(access,Objects.toString(version,"")),Account.class,()->{
            Account target=load(id,true);editable(target);Versions.check(version,target.version());
            if(target.state().equals("SUSPENDED"))Versions.conflict("Reactiva la cuenta antes de modificar sus accesos.");
            if(!target.emailVerified())Versions.conflict("La persona debe verificar su correo antes de aprobar o ampliar sus accesos.");
            validateAccess(actor,access);grant(actor,id,childKey,access);
            jdbc.update("UPDATE user_account SET access_state='APPROVED',access_version=access_version+1,decided_by=?,decided_at=now(),decision_note='' WHERE id=?",actor.id(),id);
            audit.record(actor.id(),"ACCOUNT_ACCESS_APPROVED",id,Map.of("role",access.role(),"projects",access.projects().size()));return load(id,false);
        });
    }
    @Transactional
    public Account decide(Actor actor,UUID id,String action,String key,String version,Decision input){
        manager(actor);
        return commands.execute(actor.id(),"POST /accounts/"+id+"/"+action,key,List.of(input,Objects.toString(version,"")),Account.class,()->{
            Account target=load(id,true);editable(target);Versions.check(version,target.version());
            switch(action){
                case "reject"->{if(!target.state().equals("PENDING"))Versions.conflict("Solo se rechazan solicitudes pendientes.");jdbc.update("UPDATE user_account SET access_state='REJECTED' WHERE id=?",id);}
                case "suspend"->{if(target.state().equals("SUSPENDED"))Versions.conflict("La cuenta ya está suspendida.");jdbc.update("UPDATE user_account SET enabled=false WHERE id=?",id);}
                case "resume"->{if(!target.state().equals("SUSPENDED"))Versions.conflict("La cuenta ya está habilitada.");jdbc.update("UPDATE user_account SET enabled=true WHERE id=?",id);}
                default->throw ApiException.missing();
            }
            jdbc.update("UPDATE user_account SET access_version=access_version+1,decided_by=?,decided_at=now(),decision_note=? WHERE id=?",actor.id(),input.reason().strip(),id);
            if(!action.equals("resume"))jdbc.update("DELETE FROM spring_session WHERE principal_name=(SELECT subject FROM user_account WHERE id=?)",id);
            audit.record(actor.id(),"ACCOUNT_"+action.toUpperCase(Locale.ROOT),id,Map.of("reason",input.reason().strip(),"fromVersion",target.version(),"toVersion",target.version()+1));return load(id,false);
        });
    }
    @Transactional
    public Account retryActivation(Actor actor,UUID id,String key,String version){
        manager(actor);
        return commands.execute(actor.id(),"POST /accounts/"+id+"/activation",key,Objects.toString(version,""),Account.class,()->{
            var target=load(id,true);editable(target);Versions.check(version,target.version());
            if(!target.state().equals("APPROVED")||target.emailVerified())Versions.conflict("Esta cuenta no necesita activación.");
            if(jdbc.update("UPDATE account_activation SET state='PENDING',attempts=0,available_at=now(),error_code=NULL,lease_until=NULL,lease_token=NULL WHERE user_id=? AND (state<>'SENDING' OR lease_until<now())",id)==0)Versions.conflict("La activación no está disponible o ya está en proceso.");
            jdbc.update("UPDATE user_account SET access_version=access_version+1 WHERE id=?",id);audit.record(actor.id(),"ACCOUNT_ACTIVATION_REQUEUED",id);return load(id,false);
        });
    }
}
