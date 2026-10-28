package com.daraya.atoconstrucciones.documents;
import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.identity.SensitiveAuthenticationPolicy;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.shared.*;
import jakarta.validation.constraints.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service
public class PrivacyService {
    public record NoticeInput(@NotBlank @Size(max=200)String controllerName,@NotBlank @Email @Size(max=254)String contactEmail,@NotBlank @Size(max=10000)String content){}
    public record Notice(UUID id,int revision,String state,String controllerName,String contactEmail,String content){}
    public record ConsentInput(@NotNull UUID projectId,@NotNull UUID documentId,@NotNull UUID noticeId,@NotNull UUID evidenceId,
        @NotBlank @Size(max=500)String purpose,@NotBlank @Size(max=500)String channels,@NotNull LocalDate expiresOn){}
    public record Consent(UUID id,UUID projectId,UUID documentId,UUID noticeId,UUID evidenceId,UUID grantedBy,String purpose,String channels,LocalDate expiresOn,Instant withdrawnAt,long version){}
    public record RequestInput(@NotNull @Pattern(regexp="ACCESS|RECTIFICATION|WITHDRAWAL")String kind,@NotBlank @Size(max=2000)String details){}
    public record PrivacyRequest(UUID id,UUID actorId,String kind,String details,String state,String resolution){}
    public record Retention(String documentClass,String policy,boolean automaticDeletion,boolean legalHold){}
    private final JdbcTemplate jdbc;private final ProjectService projects;private final DocumentService documents;private final AuditService audit;private final IdempotencyService commands;
    private final SensitiveAuthenticationPolicy sensitive;
    public PrivacyService(JdbcTemplate jdbc,ProjectService projects,DocumentService documents,AuditService audit,IdempotencyService commands,SensitiveAuthenticationPolicy sensitive){this.sensitive=sensitive;this.jdbc=jdbc;this.projects=projects;this.documents=documents;this.audit=audit;this.commands=commands;}
    public List<Retention> retention(){return List.of(new Retention("CONTRACT","Conservar; plazo permanente pendiente de aprobación",false,true),new Retention("EVIDENCE","Conservar; plazo permanente pendiente de aprobación",false,true),new Retention("PHOTO","Conservar en privado; publicación requiere consentimiento vigente",false,false),new Retention("RESERVATION","Reserva sin objeto caduca en una hora; no borra evidencia",false,false));}
    private Notice loadNotice(UUID id){var rows=jdbc.query("SELECT * FROM privacy_notice WHERE id=?",(r,n)->new Notice(r.getObject("id",UUID.class),r.getInt("revision"),r.getString("state"),r.getString("controller_name"),r.getString("contact_email"),r.getString("content")),id);if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();}
    public List<Notice> notices(Actor actor){
        String where=actor.manager()?"":" WHERE state='APPROVED'";
        var ids=jdbc.queryForList("SELECT id FROM privacy_notice"+where+" ORDER BY revision DESC",UUID.class);return ids.stream().map(this::loadNotice).toList();
    }
    @Transactional public Notice draft(Actor actor,String key,NoticeInput input){actor.requireManager();return commands.execute(actor.id(),"POST /privacy/notices",key,input,Notice.class,()->{
        jdbc.queryForObject("SELECT pg_advisory_xact_lock(47002013)",(r,n)->1);UUID id=UUID.randomUUID();int rev=jdbc.queryForObject("SELECT coalesce(max(revision),0)+1 FROM privacy_notice",Integer.class);
        jdbc.update("INSERT INTO privacy_notice(id,revision,state,controller_name,contact_email,content) VALUES (?,?,'DRAFT',?,?,?)",id,rev,input.controllerName().trim(),input.contactEmail().trim().toLowerCase(Locale.ROOT),input.content());audit.record(actor.id(),"PRIVACY_NOTICE_DRAFTED",id);return loadNotice(id);
    });}
    @Transactional public Notice approve(Actor actor,UUID id,String key){actor.requireManager();sensitive.require(actor);return commands.execute(actor.id(),"POST /privacy/notices/"+id+"/approve",key,id,Notice.class,()->{
        jdbc.queryForObject("SELECT id FROM privacy_notice WHERE id=? FOR UPDATE",UUID.class,id);Notice notice=loadNotice(id);
        if(!notice.state().equals("DRAFT"))Versions.conflict("El aviso ya fue aprobado.");
        jdbc.update("UPDATE privacy_notice SET state='APPROVED',approved_by=?,approved_at=now() WHERE id=?",actor.id(),id);audit.record(actor.id(),"PRIVACY_NOTICE_APPROVED",id,Map.of("revision",notice.revision()));return loadNotice(id);
    });}
    private Consent loadConsent(UUID id,boolean lock){var rows=jdbc.query("SELECT * FROM publication_consent WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new Consent(r.getObject("id",UUID.class),r.getObject("project_id",UUID.class),r.getObject("document_id",UUID.class),r.getObject("notice_id",UUID.class),r.getObject("evidence_id",UUID.class),r.getObject("granted_by",UUID.class),r.getString("purpose"),r.getString("channels"),r.getObject("expires_on",LocalDate.class),r.getTimestamp("withdrawn_at")==null?null:r.getTimestamp("withdrawn_at").toInstant(),r.getLong("version")),id);if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();}
    /** Contrato entre módulos: finalidad y medio exactos, sin interpretar texto libre. */
    @Transactional public Consent requireWebsiteConsent(Actor actor,UUID consentId,UUID documentId){
        Consent c=loadConsent(consentId,true);projects.operations(actor,c.projectId());
        if(!c.documentId().equals(documentId)||c.withdrawnAt()!=null||c.expiresOn().isBefore(LocalDate.now(ZoneId.of("America/Costa_Rica")))||!c.purpose().equals("PORTFOLIO")||!c.channels().equals("WEBSITE"))
            Versions.conflict("Seleccione consentimiento vigente para PORTFOLIO / WEBSITE de esta fotografía.");
        documents.requireEvidence(actor,c.projectId(),c.documentId());documents.requireEvidence(actor,c.projectId(),c.evidenceId());return c;
    }
    public List<Consent> websiteConsents(Actor actor,UUID p){
        projects.operations(actor,p);return jdbc.queryForList("SELECT id FROM publication_consent WHERE project_id=? AND purpose='PORTFOLIO' AND channels='WEBSITE' ORDER BY granted_at",UUID.class,p).stream().map(id->loadConsent(id,false)).toList();
    }
    public Notice publicNotice(){
        var ids=jdbc.queryForList("SELECT id FROM privacy_notice WHERE state='APPROVED' ORDER BY revision DESC LIMIT 1",UUID.class);
        return ids.isEmpty()?null:loadNotice(ids.getFirst());
    }
    public List<Consent> consents(Actor actor,UUID p){projects.authorize(actor,p);if(!actor.manager())projects.representative(actor,p);return jdbc.queryForList("SELECT id FROM publication_consent WHERE project_id=? ORDER BY granted_at",UUID.class,p).stream().map(id->loadConsent(id,false)).toList();}
    @Transactional public Consent grant(Actor actor,String key,ConsentInput input){
        projects.authorize(actor,input.projectId());if(!actor.manager())projects.representative(actor,input.projectId());else sensitive.require(actor);
        if(input.expiresOn().isBefore(LocalDate.now(ZoneId.of("America/Costa_Rica"))))Versions.invalid("El consentimiento no puede estar vencido.");
        return commands.execute(actor.id(),"POST /privacy/consents",key,input,Consent.class,()->{
            projects.writable(projects.lock(actor,input.projectId()));Notice notice=loadNotice(input.noticeId());
            if(!notice.state().equals("APPROVED"))Versions.conflict("El aviso de privacidad debe estar aprobado.");
            documents.requireEvidence(actor,input.projectId(),input.documentId());documents.requireEvidence(actor,input.projectId(),input.evidenceId());
            if(!documents.read(actor,input.documentId()).documentClass().equals("PHOTO"))Versions.invalid("El consentimiento de publicación identifica una fotografía.");
            UUID id=UUID.randomUUID();jdbc.update("INSERT INTO publication_consent(id,project_id,document_id,notice_id,evidence_id,granted_by,purpose,channels,expires_on) VALUES (?,?,?,?,?,?,?,?,?)",id,input.projectId(),input.documentId(),input.noticeId(),input.evidenceId(),actor.id(),input.purpose(),input.channels(),input.expiresOn());
            documents.retain(actor,input.projectId(),input.evidenceId(),"CONSENT_"+id);audit.record(actor.id(),"PUBLICATION_CONSENT_GRANTED",input.projectId(),Map.of("consentId",id,"noticeId",input.noticeId(),"documentId",input.documentId()));return loadConsent(id,false);
        });
    }
    @Transactional public Consent withdraw(Actor actor,UUID id,String key,String etag,String reason){
        Consent consent=loadConsent(id,false);projects.authorize(actor,consent.projectId());if(!actor.manager()&&!actor.id().equals(consent.grantedBy()))projects.representative(actor,consent.projectId());
        return commands.execute(actor.id(),"POST /privacy/consents/"+id+"/withdraw",key,List.of(reason,Objects.toString(etag,"")),Consent.class,()->{
            Consent c=loadConsent(id,true);Versions.check(etag,c.version());if(c.withdrawnAt()!=null)Versions.conflict("El consentimiento ya fue retirado.");
            jdbc.update("UPDATE publication_consent SET withdrawn_at=now(),withdrawal_reason=?,version=version+1 WHERE id=?",reason,id);audit.record(actor.id(),"PUBLICATION_CONSENT_WITHDRAWN",c.projectId(),Map.of("consentId",id,"reason",reason));return loadConsent(id,false);
        });
    }
    private PrivacyRequest loadRequest(UUID id){var rows=jdbc.query("SELECT * FROM privacy_request WHERE id=?",(r,n)->new PrivacyRequest(r.getObject("id",UUID.class),r.getObject("actor_id",UUID.class),r.getString("kind"),r.getString("details"),r.getString("state"),r.getString("resolution")),id);if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();}
    public List<PrivacyRequest> requests(Actor actor){return jdbc.queryForList("SELECT id FROM privacy_request WHERE ? OR actor_id=? ORDER BY created_at",UUID.class,actor.manager(),actor.id()).stream().map(this::loadRequest).toList();}
    @Transactional public PrivacyRequest request(Actor actor,String key,RequestInput input){return commands.execute(actor.id(),"POST /privacy/requests",key,input,PrivacyRequest.class,()->{UUID id=UUID.randomUUID();jdbc.update("INSERT INTO privacy_request(id,actor_id,kind,details) VALUES (?,?,?,?)",id,actor.id(),input.kind(),input.details());audit.record(actor.id(),"PRIVACY_REQUEST_CREATED",id);return loadRequest(id);});}
    @Transactional public PrivacyRequest resolve(Actor actor,UUID id,String key,String resolution){actor.requireManager();sensitive.require(actor);return commands.execute(actor.id(),"POST /privacy/requests/"+id+"/resolve",key,resolution,PrivacyRequest.class,()->{
        var rows=jdbc.queryForList("SELECT id FROM privacy_request WHERE id=? FOR UPDATE",UUID.class,id);if(rows.isEmpty())throw ApiException.missing();if(!loadRequest(id).state().equals("PENDING"))Versions.conflict("La solicitud ya fue atendida.");
        jdbc.update("UPDATE privacy_request SET state='RESOLVED',resolution=?,resolved_by=? WHERE id=?",resolution,actor.id(),id);audit.record(actor.id(),"PRIVACY_REQUEST_RESOLVED",id);return loadRequest(id);
    });}
}
