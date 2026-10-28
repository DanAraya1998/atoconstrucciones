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
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.io.*;
import java.time.*;
import java.util.*;

@Service
public class DocumentService {
    public record Reservation(@NotNull UUID projectId,@NotBlank @Size(max=160) String filename,
        @NotBlank String mediaType,@Min(1) long size,@NotBlank @Pattern(regexp="[a-f0-9]{64}") String sha256,
        @NotNull @Pattern(regexp="CONTRACT|EVIDENCE|PHOTO") String documentClass) {}
    public record Document(UUID id,UUID projectId,String filename,String mediaType,long size,String sha256,String state,
        String audience,UUID recipientId,String documentClass,Instant expiresAt,String scanError,long version) {}
    public record UploadSession(Document document,String uploadPath,String method) {}
    public record Page(List<Document> items) {}
    public record Sharing(@NotNull @Pattern(regexp="INTERNAL|CLIENT|SUPPLIER") String audience,UUID recipientId,@NotBlank @Size(max=500) String reason) {}
    public record Link(@NotNull UUID projectId,@NotBlank @Pattern(regexp="[A-Z0-9_]{3,40}") String purpose) {}
    public record Download(byte[] bytes,Document document) {}
    private record ObjectRow(Document document,UUID owner,String key,String objectVersion,String provider) {}
    private final JdbcTemplate jdbc;private final ProjectService projects;private final ObjectStoragePort storage;private final ScannerPort scanner;
    private final AuditService audit;private final IdempotencyService commands;private final TransactionTemplate tx;
    private final SensitiveAuthenticationPolicy sensitive;
    public DocumentService(JdbcTemplate jdbc,ProjectService projects,ObjectStoragePort storage,ScannerPort scanner,AuditService audit,IdempotencyService commands,PlatformTransactionManager transactionManager,SensitiveAuthenticationPolicy sensitive){this.sensitive=sensitive;this.jdbc=jdbc;this.projects=projects;this.storage=storage;this.scanner=scanner;this.audit=audit;this.commands=commands;this.tx=new TransactionTemplate(transactionManager);}
    private ObjectRow load(UUID id,boolean lock){
        var rows=jdbc.query("SELECT * FROM document WHERE id=?"+(lock?" FOR UPDATE":""),(r,n)->new ObjectRow(new Document(r.getObject("id",UUID.class),r.getObject("project_id",UUID.class),r.getString("filename"),r.getString("media_type"),r.getLong("expected_size"),r.getString("expected_sha256"),r.getString("state"),r.getString("audience"),r.getObject("recipient_id",UUID.class),r.getString("document_class"),r.getTimestamp("expires_at").toInstant(),r.getString("scan_error"),r.getLong("version")),r.getObject("owner_id",UUID.class),r.getString("object_key"),r.getString("object_version"),r.getString("storage_provider")),id);
        if(rows.isEmpty())throw ApiException.missing();return rows.getFirst();
    }
    private void requireDrive(ObjectRow row){
        if(!row.provider().equals("GOOGLE_DRIVE"))throw new ApiException(503,"STORAGE_MIGRATION_REQUIRED","La evidencia anterior requiere migración verificada a Google Drive.");
    }
    private void access(Actor actor,ObjectRow row){
        var d=row.document();projects.authorize(actor,d.projectId());
        if(actor.manager()||projects.capacity(actor,d.projectId(),"OPERATIONS")||row.owner().equals(actor.id()))return;
        if(d.audience().equals("CLIENT")&&projects.capacity(actor,d.projectId(),"CLIENT"))return;
        if(d.audience().equals("SUPPLIER")&&actor.id().equals(d.recipientId())&&projects.capacity(actor,d.projectId(),"SUPPLIER"))return;
        throw ApiException.missing();
    }
    public Document read(Actor actor,UUID id){var row=load(id,false);access(actor,row);return row.document();}
    public Page list(Actor actor,UUID p){
        projects.authorize(actor,p);
        // Obra y audiencia aplicadas en SQL; el propietario aún necesita membresía vigente.
        boolean ops=actor.manager()||projects.capacity(actor,p,"OPERATIONS"),client=projects.capacity(actor,p,"CLIENT"),supplier=projects.capacity(actor,p,"SUPPLIER");
        var ids=jdbc.queryForList("SELECT id FROM document WHERE project_id=? AND (? OR owner_id=? OR (audience='CLIENT' AND ?) OR (audience='SUPPLIER' AND recipient_id=? AND ?)) ORDER BY created_at,id",UUID.class,p,ops,actor.id(),client,actor.id(),supplier);
        return new Page(ids.stream().map(id->read(actor,id)).toList());
    }
    @Transactional
    public UploadSession reserve(Actor actor,String key,Reservation input){
        projects.authorize(actor,input.projectId());long limit=FileInspection.limit(input.mediaType());
        if(input.size()>limit)Versions.invalid("El archivo excede el límite del tipo permitido.");
        if(input.filename().chars().anyMatch(c->c<32||c==127||c==47||c==92)||input.filename().startsWith("."))Versions.invalid("Nombre de archivo inválido.");
        String extension=switch(input.mediaType()){case "application/pdf"->".pdf";case "application/xml"->".xml";case "image/png"->".png";case "image/jpeg"->".jpg";default->"";};
        String name=input.filename().toLowerCase(Locale.ROOT);if(!(name.endsWith(extension)||input.mediaType().equals("image/jpeg")&&name.endsWith(".jpeg")))Versions.invalid("La extensión no corresponde al tipo declarado.");
        if(input.documentClass().equals("PHOTO")&&!input.mediaType().startsWith("image/"))Versions.invalid("Una fotografía debe ser una imagen.");
        if(!storage.available())throw new ApiException(503,"STORAGE_UNAVAILABLE","El almacenamiento de evidencias no está configurado.");
        return commands.execute(actor.id(),"POST /documents/upload-sessions",key,input,UploadSession.class,()->{
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(hashtextextended(?, 1))",(r,n)->1,actor.id().toString());
            projects.writable(projects.lock(actor,input.projectId()));
            // Serializar reservas por obra evita saltarse cuotas con solicitudes concurrentes.
            long bytes=jdbc.queryForObject("SELECT coalesce(sum(expected_size),0) FROM document WHERE project_id=? AND state NOT IN ('EXPIRED','REJECTED')",Long.class,input.projectId());
            int pending=jdbc.queryForObject("SELECT count(*) FROM document WHERE owner_id=? AND state IN ('RESERVED','QUARANTINED')",Integer.class,actor.id());
            if(bytes+input.size()>1024L*1024*1024||pending>=10)throw new ApiException(429,"UPLOAD_QUOTA_EXCEEDED","Se alcanzó la cuota de evidencias o archivos pendientes.");
            UUID id=UUID.randomUUID();jdbc.update("INSERT INTO document(id,project_id,owner_id,filename,media_type,expected_size,expected_sha256,object_key,document_class) VALUES (?,?,?,?,?,?,?,?,?)",id,input.projectId(),actor.id(),input.filename(),input.mediaType(),input.size(),input.sha256(),"business/"+input.projectId()+"/"+id,input.documentClass());
            audit.record(actor.id(),"DOCUMENT_RESERVED",input.projectId(),Map.of("documentId",id));
            return new UploadSession(load(id,false).document(),"/api/v1/documents/"+id+"/content","PUT");
        });
    }
    @Transactional
    public Document upload(Actor actor,UUID id,InputStream input,long contentLength){
        var unlocked=load(id,false);access(actor,unlocked);projects.writable(projects.lock(actor,unlocked.document().projectId()));
        var row=load(id,true);access(actor,row);var d=row.document();
        if(!actor.id().equals(row.owner()))throw ApiException.forbidden();
        projects.writable(projects.lock(actor,d.projectId()));
        if(!d.state().equals("RESERVED"))Versions.conflict("La reserva ya recibió contenido.");
        if(d.expiresAt().isBefore(Instant.now()))Versions.conflict("La reserva caducó.");
        if(contentLength!=d.size())Versions.invalid("El tamaño no coincide con la reserva.");
        byte[] bytes;try{bytes=input.readNBytes(Math.toIntExact(d.size()+1));}catch(IOException e){throw new ApiException(400,"INCOMPLETE_UPLOAD","Carga incompleta.");}
        if(bytes.length!=d.size()||!FileInspection.sha256(bytes).equals(d.sha256()))Versions.invalid("El tamaño o hash real no coincide.");
        // Validación estructural se ejecuta después del antivirus en el worker aislado del comando financiero.
        requireDrive(row);
        String version=storage.put(row.key(),bytes,d.mediaType(),d.filename());
        jdbc.update("UPDATE document SET object_version=?,state='QUARANTINED',version=version+1 WHERE id=?",version,id);
        audit.record(actor.id(),"DOCUMENT_UPLOADED",d.projectId(),Map.of("documentId",id));return load(id,false).document();
    }
    @Transactional
    public Document complete(Actor actor,UUID id,String key){
        var row=load(id,false);access(actor,row);if(!row.owner().equals(actor.id())&&!actor.manager())throw ApiException.forbidden();
        return commands.execute(actor.id(),"POST /documents/"+id+"/complete-upload",key,id,Document.class,()->{
            var current=load(id,true);if(!Set.of("QUARANTINED","CLEAN","INFECTED","REJECTED").contains(current.document().state())||current.objectVersion()==null)Versions.conflict("El objeto todavía no está completo.");
            if(current.document().state().equals("QUARANTINED")){requireDrive(current);storage.get(current.key(),current.objectVersion(),current.document().size());}
            return current.document();
        });
    }
    @Transactional
    public Document share(Actor actor,UUID id,String key,String etag,Sharing input){
        actor.requireManager();sensitive.require(actor);
        return commands.execute(actor.id(),"PUT /documents/"+id+"/sharing",key,List.of(input,Objects.toString(etag,"")),Document.class,()->{
            var unlocked=load(id,false);projects.writable(projects.lock(actor,unlocked.document().projectId()));
            var row=load(id,true);var d=row.document();Versions.check(etag,d.version());
            if(!d.state().equals("CLEAN"))Versions.conflict("Solo se puede compartir un documento limpio.");
            if(!input.audience().equals("INTERNAL")&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM document_link WHERE document_id=? AND (purpose LIKE 'EXPENSE%' OR purpose LIKE 'COST_VA%' OR purpose LIKE 'FISCAL%' OR purpose LIKE 'CASH_%' OR purpose LIKE 'TRANSFER%'))",Boolean.class,id)))Versions.conflict("La evidencia financiera interna no se comparte fuera de operación.");
            if(input.audience().equals("SUPPLIER")&&(d.documentClass().equals("CONTRACT")||Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM document_link WHERE document_id=? AND (purpose='CONTRACT_BASE' OR purpose LIKE 'AMENDME%' OR purpose LIKE 'EXTRA_A%'))",Boolean.class,id))))Versions.conflict("La evidencia contractual no se comparte con proveedores.");
            if(input.audience().equals("SUPPLIER")!=(input.recipientId()!=null))Versions.invalid("Seleccione una cuenta destinataria para compartir con proveedor.");
            if(input.recipientId()!=null){
                Actor recipient=actorFor(input.recipientId());if(recipient==null||!projects.capacity(recipient,d.projectId(),"SUPPLIER"))Versions.invalid("El proveedor no tiene asignación vigente en esta obra.");
            }
            jdbc.update("UPDATE document SET audience=?,recipient_id=?,version=version+1 WHERE id=?",input.audience(),input.recipientId(),id);
            audit.record(actor.id(),"DOCUMENT_SHARED",d.projectId(),Map.of("documentId",id,"audience",input.audience(),"reason",input.reason()));return load(id,false).document();
        });
    }
    @Transactional
    public Document link(Actor actor,UUID id,String key,Link input){
        var row=load(id,false);access(actor,row);projects.operations(actor,input.projectId());
        if(!row.document().projectId().equals(input.projectId()))throw ApiException.missing();
        return commands.execute(actor.id(),"POST /documents/"+id+"/links",key,input,Document.class,()->{
            projects.writable(projects.lock(actor,input.projectId()));requireEvidence(actor,input.projectId(),id);retain(actor,input.projectId(),id,input.purpose());return load(id,false).document();
        });
    }
    @Transactional
    public void requireEvidence(Actor actor,UUID project,UUID id){
        var row=load(id,true);if(!row.document().projectId().equals(project))throw ApiException.missing();access(actor,row);
        if(!row.document().state().equals("CLEAN")||row.objectVersion()==null)Versions.conflict("La evidencia debe estar completa y limpia.");
    }
    @Transactional
    public void retain(Actor actor,UUID project,UUID id,String purpose){
        requireEvidence(actor,project,id);
        if(purpose.startsWith("EXPENSE")||purpose.startsWith("COST_VALIDATION")||purpose.startsWith("FISCAL")||purpose.startsWith("CASH")||purpose.startsWith("TRANSFER")){
            var row=load(id,false);
            if(!row.document().audience().equals("INTERNAL")||!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM user_role WHERE user_id=? AND role IN ('GERENTE','ASISTENTE_ADMINISTRATIVA'))",Boolean.class,row.owner())))Versions.conflict("La evidencia financiera requiere audiencia interna y propietario de operación.");
        }
        // Finalidades internas con UUID pueden superar 40 caracteres; el contrato público limita su entrada.
        String tag=purpose.length()>40?purpose.substring(0,7)+"_"+IdempotencyService.hash(purpose).substring(0,32):purpose;
        if(purpose.startsWith("CONTRACT")||purpose.startsWith("AMENDMENT")||purpose.startsWith("EXTRA_ACCEPTANCE")){
            if(load(id,false).document().audience().equals("SUPPLIER"))Versions.conflict("Cambie la audiencia contractual antes de vincularla.");
        }
        jdbc.update("INSERT INTO document_link(document_id,project_id,purpose,created_by) VALUES (?,?,?,?) ON CONFLICT DO NOTHING",id,project,tag,actor.id());
        jdbc.update("INSERT INTO document_hold(document_id,reason,actor_id) VALUES (?,'Evidencia vinculada; conservación pendiente de política aprobada',?) ON CONFLICT DO NOTHING",id,actor.id());
        audit.record(actor.id(),"DOCUMENT_LINKED",project,Map.of("documentId",id,"purpose",tag));
    }
    @Transactional
    public Download download(Actor actor,UUID id){
        var row=load(id,true);access(actor,row);if(!row.document().state().equals("CLEAN"))Versions.conflict("Archivo no habilitado para descarga.");
        requireDrive(row);
        byte[] bytes=storage.get(row.key(),row.objectVersion(),row.document().size()).bytes();
        if(!FileInspection.sha256(bytes).equals(row.document().sha256()))throw new ApiException(503,"OBJECT_INTEGRITY_FAILURE","La evidencia no pasó la verificación de integridad.");
        audit.record(actor.id(),"DOCUMENT_DOWNLOADED",row.document().projectId(),Map.of("documentId",id));return new Download(bytes,row.document());
    }
    private Actor actorFor(UUID id){
        var rows=jdbc.queryForList("SELECT email FROM user_account WHERE id=? AND enabled",String.class,id);if(rows.isEmpty())return null;
        return new Actor(id,rows.getFirst(),Set.copyOf(jdbc.queryForList("SELECT role FROM user_role WHERE user_id=?",String.class,id)),false,null);
    }
    public boolean scanNext(){
        UUID token=UUID.randomUUID();ObjectRow row=tx.execute(status->{
            var ids=jdbc.queryForList("SELECT id FROM document WHERE state='QUARANTINED' AND scan_after<=now() AND (scan_until IS NULL OR scan_until<now()) ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1",UUID.class);
            if(ids.isEmpty())return null;UUID id=ids.getFirst();jdbc.update("UPDATE document SET scan_lease=?,scan_until=now()+interval '60 seconds',scan_attempts=scan_attempts+1 WHERE id=?",token,id);return load(id,false);
        });
        if(row==null)return false;
        String state="CLEAN",error=null;
        try{
            Actor owner=actorFor(row.owner());if(owner==null)throw ApiException.missing();projects.authorize(owner,row.document().projectId());
            requireDrive(row);
            byte[] bytes=storage.get(row.key(),row.objectVersion(),row.document().size()).bytes();
            if(bytes.length!=row.document().size()||!FileInspection.sha256(bytes).equals(row.document().sha256()))throw new ApiException(422,"OBJECT_INTEGRITY_FAILURE","Integridad inválida.");
            if(scanner.scan(bytes)==ScannerPort.Result.INFECTED){state="INFECTED";error="MALWARE_DETECTED";}
            else BoundedFileInspection.inspect(bytes,row.document().mediaType());
        }catch(ApiException e){state=e.status()==503?"QUARANTINED":"REJECTED";error=e.code();}
        catch(RuntimeException e){state="QUARANTINED";error="SCAN_FAILED";}
        final String result=state,code=error;
        tx.executeWithoutResult(status->{
            var current=load(row.document().id(),true);
            if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM document WHERE id=? AND scan_lease=? AND state='QUARANTINED' AND scan_until>now())",Boolean.class,current.document().id(),token)))return;
            String authorizedResult=result;String authorizedError=code;Actor owner=actorFor(row.owner());
            try{if(owner==null)throw ApiException.missing();projects.authorize(owner,current.document().projectId());}catch(ApiException e){authorizedResult="REJECTED";authorizedError="ACCESS_REVOKED";}
            jdbc.update("UPDATE document SET state=?,scan_error=?,scanned_at=CASE WHEN ?='QUARANTINED' THEN NULL ELSE now() END,scan_after=now()+make_interval(secs=>least(3600,5*power(2,least(scan_attempts,10))::integer)),scan_lease=NULL,scan_until=NULL,version=version+1 WHERE id=?",authorizedResult,authorizedError,authorizedResult,current.document().id());
            audit.record(row.owner(),"DOCUMENT_SCAN_"+authorizedResult,current.document().projectId(),Map.of("documentId",current.document().id(),"error",Objects.toString(authorizedError,"")));
        });return true;
    }
    @Transactional
    public int expireReservations(){
        // No se eliminan originales ni evidencias: política permanente todavía pendiente.
        var rows=jdbc.query("SELECT id,project_id,owner_id FROM document WHERE state='RESERVED' AND expires_at<now() FOR UPDATE SKIP LOCKED",(r,n)->List.of(r.getObject(1,UUID.class),r.getObject(2,UUID.class),r.getObject(3,UUID.class)));
        for(var row:rows){jdbc.update("UPDATE document SET state='EXPIRED',version=version+1 WHERE id=?",row.get(0));audit.record(row.get(2),"DOCUMENT_RESERVATION_EXPIRED",row.get(1),Map.of("documentId",row.get(0)));}return rows.size();
    }
}
