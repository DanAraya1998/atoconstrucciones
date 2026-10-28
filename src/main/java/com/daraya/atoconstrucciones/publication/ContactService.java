package com.daraya.atoconstrucciones.publication;

import com.daraya.atoconstrucciones.documents.PrivacyService;
import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.util.*;
import java.time.Instant;

@Service
public class ContactService {
    public record Input(@NotBlank @Size(max=160)String name,@NotBlank @Email @Size(max=254)String email,
        @NotBlank @Size(min=10,max=3000)String message,@NotNull UUID noticeId,@AssertTrue boolean privacyAccepted,@NotNull @Size(max=200)String website){}
    public record Receipt(UUID reference,String status){}
    public record Contact(UUID id,String name,String email,String message,UUID noticeId,Instant receivedAt,String deliveryState,int attempts,String errorCode){}
    public record Delegate(@NotNull UUID userId,boolean active){}
    private final JdbcTemplate jdbc;private final PrivacyService privacy;private final JsonMapper json;private final SensitiveAuthenticationPolicy sensitive;private final AuditService audit;private final IdempotencyService commands;
    public ContactService(JdbcTemplate jdbc,PrivacyService privacy,JsonMapper json,SensitiveAuthenticationPolicy sensitive,AuditService audit,IdempotencyService commands){this.jdbc=jdbc;this.privacy=privacy;this.json=json;this.sensitive=sensitive;this.audit=audit;this.commands=commands;}
    @Transactional public Receipt receive(String key,Input input){
        if(key==null||!key.matches("[a-fA-F0-9]{8}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{4}-[a-fA-F0-9]{12}"))throw new ApiException(400,"IDEMPOTENCY_KEY_REQUIRED","Incluya una clave UUID de idempotencia.");
        if(!input.privacyAccepted())Versions.invalid("Debe aceptar el aviso de privacidad.");
        if(!input.website().isBlank())return new Receipt(UUID.randomUUID(),"RECIBIDO");
        String hash=IdempotencyService.hash(key),body=IdempotencyService.hash(json.writeValueAsString(input));
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?,27))",(r,n)->1,hash);
        var prior=jdbc.queryForList("SELECT id,body_hash FROM contact_request WHERE key_hash=?",hash);
        if(!prior.isEmpty()){
            if(!body.equals(prior.getFirst().get("body_hash")))throw new ApiException(409,"IDEMPOTENCY_CONFLICT","La clave ya corresponde a otra solicitud.");
            return new Receipt((UUID)prior.getFirst().get("id"),"RECIBIDO");
        }
        var notice=privacy.publicNotice();
        if(notice==null)throw new ApiException(503,"PRIVACY_NOTICE_PENDING","El formulario estará disponible al aprobar el aviso de privacidad.");
        if(!notice.id().equals(input.noticeId()))throw new ApiException(409,"NOTICE_CHANGED","El aviso cambió. Revise la versión vigente antes de enviar.");
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO contact_request(id,key_hash,body_hash,name,email,message,notice_id) VALUES (?,?,?,?,?,?,?)",id,hash,body,input.name().trim(),input.email().trim().toLowerCase(Locale.ROOT),input.message().trim(),notice.id());
        jdbc.update("INSERT INTO contact_delivery(contact_id) VALUES (?)",id);return new Receipt(id,"RECIBIDO");
    }
    private void access(Actor actor){
        if(actor.manager())return;
        if(!actor.roles().contains("ASISTENTE_ADMINISTRATIVA")||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM contact_access_delegate WHERE user_id=? AND active)",Boolean.class,actor.id())))throw ApiException.forbidden();
    }
    @Transactional public List<Contact> list(Actor actor,int page,int size){
        access(actor);if(page<0||page>100000||size<1||size>100)throw new ApiException(400,"INVALID_PAGINATION","Paginación inválida.");
        var rows=jdbc.query("SELECT c.*,d.state,d.attempts,d.error_code FROM contact_request c JOIN contact_delivery d ON c.id=d.contact_id ORDER BY received_at DESC,c.id LIMIT ? OFFSET ?",(r,n)->new Contact(r.getObject("id",UUID.class),r.getString("name"),r.getString("email"),r.getString("message"),r.getObject("notice_id",UUID.class),r.getTimestamp("received_at").toInstant(),r.getString("state"),r.getInt("attempts"),r.getString("error_code")),size,page*size);
        audit.record(actor.id(),"CONTACTS_READ",UUID.fromString("00000000-0000-0000-0000-000000000004"),Map.of("page",page,"size",size));return rows;
    }
    @Transactional public Delegate delegate(Actor actor,String key,Delegate input){actor.requireManager();sensitive.require(actor);return commands.execute(actor.id(),"PUT /contacts/delegates",key,input,Delegate.class,()->{
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_account a JOIN user_role r ON r.user_id=a.id WHERE a.id=? AND a.enabled AND r.role='ASISTENTE_ADMINISTRATIVA')",Boolean.class,input.userId())))Versions.invalid("Seleccione una asistente activa.");
        jdbc.update("INSERT INTO contact_access_delegate(user_id,active,granted_by) VALUES (?,?,?) ON CONFLICT(user_id) DO UPDATE SET active=excluded.active,granted_by=excluded.granted_by,granted_at=now()",input.userId(),input.active(),actor.id());audit.record(actor.id(),input.active()?"CONTACT_ACCESS_GRANTED":"CONTACT_ACCESS_REVOKED",input.userId());return input;
    });}
    @Transactional public Receipt retry(Actor actor,UUID id,String key){actor.requireManager();sensitive.require(actor);return commands.execute(actor.id(),"POST /contacts/"+id+"/retry",key,id,Receipt.class,()->{
        var rows=jdbc.queryForList("SELECT state FROM contact_delivery WHERE contact_id=? FOR UPDATE",String.class,id);if(rows.isEmpty())throw ApiException.missing();if(!rows.getFirst().equals("DEAD"))Versions.conflict("Solo una notificación agotada puede reintentarse.");
        jdbc.update("UPDATE contact_delivery SET state='PENDING',attempts=0,available_at=now(),error_code=NULL,lease_token=NULL,lease_until=NULL WHERE contact_id=?",id);audit.record(actor.id(),"CONTACT_NOTIFICATION_RETRIED",id);return new Receipt(id,"RECIBIDO");
    });}
}
