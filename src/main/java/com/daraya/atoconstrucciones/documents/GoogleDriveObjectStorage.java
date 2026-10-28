package com.daraya.atoconstrucciones.documents;

import com.daraya.atoconstrucciones.shared.ApiException;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.auth.oauth2.UserCredentials;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.nio.file.*;
import java.util.*;

@Component
public class GoogleDriveObjectStorage implements ObjectStoragePort {
    private record Reference(String key,String file,String folder,String name,String type,String sha,long size,String revision) {
        String token() { return "drive:"+file+":"+revision; }
    }
    private final DriveApi drive;
    private final String folder;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate reservations;
    @Autowired
    public GoogleDriveObjectStorage(@Value("${ato.storage.enabled:false}") boolean enabled,
            @Value("${ato.storage.drive.folder-id:}") String folder,
            @Value("${ato.storage.drive.credentials-file:}") String credentialsFile,
            @Value("${ato.storage.drive.scope:https://www.googleapis.com/auth/drive.file}") String scope,
            JdbcTemplate jdbc,PlatformTransactionManager transactionManager,JsonMapper json) {
        this(enabled?configuredApi(folder,credentialsFile,scope,json):null,folder,jdbc,transactionManager);
    }
    GoogleDriveObjectStorage(DriveApi drive,String folder,JdbcTemplate jdbc,PlatformTransactionManager transactionManager) {
        this.drive=drive;this.folder=folder;this.jdbc=jdbc;
        this.reservations=new TransactionTemplate(transactionManager);
        this.reservations.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }
    private static DriveApi configuredApi(String folder,String credentialsFile,String scope,JsonMapper json) {
        if(!folder.matches("[A-Za-z0-9_-]{1,100}")||credentialsFile.isBlank()||
                !Set.of("https://www.googleapis.com/auth/drive.file","https://www.googleapis.com/auth/drive").contains(scope))
            throw new IllegalStateException("Configure carpeta, credenciales y scope de Google Drive.");
        try {
            // Solo configuraciones de servidor tipadas; no se reciben credenciales del portal.
            byte[] bytes;
            try(var input=Files.newInputStream(Path.of(credentialsFile))){bytes=input.readNBytes(65537);}
            if(bytes.length>65536)throw new IOException("Credential limit");
            var config=json.readTree(bytes);
            String tokenUri=config.path("token_uri").asString("https://oauth2.googleapis.com/token");
            if(!Set.of("https://oauth2.googleapis.com/token","https://accounts.google.com/o/oauth2/token").contains(tokenUri))
                throw new IOException("Credential endpoint");
            GoogleCredentials credentials=switch(config.path("type").asString()) {
                case "authorized_user" -> UserCredentials.fromStream(new ByteArrayInputStream(bytes));
                case "service_account" -> ServiceAccountCredentials.fromStream(new ByteArrayInputStream(bytes)).createScoped(List.of(scope));
                default -> throw new IOException("Unsupported credential type");
            };
            return new DriveApi(json,()->{credentials.refreshIfExpired();return credentials.getAccessToken().getTokenValue();},credentials instanceof ServiceAccountCredentials);
        } catch(Exception e) {
            // No propagar mensajes del SDK que puedan contener rutas, tokens o datos privados.
            throw new IllegalStateException("No se pudieron cargar las credenciales de Google Drive.");
        }
    }
    public boolean available() { return drive!=null; }
    private void ready() {
        if(!available())throw new ApiException(503,"STORAGE_UNAVAILABLE","Google Drive no está configurado para evidencias.");
    }
    private void checkFolder() {
        var root=drive.file(folder);
        if(!root.path("mimeType").asString().equals("application/vnd.google-apps.folder")||root.path("trashed").asBoolean(false))
            throw DriveApi.unavailable();
        if(drive.requiresSharedDrive&&root.path("driveId").asString("").isEmpty())
            throw new ApiException(503,"STORAGE_SHARED_DRIVE_REQUIRED","Una cuenta de servicio requiere una unidad compartida de Google Workspace.");
        drive.privatePermissions(folder);
    }
    private Reference find(String key) {
        var references=jdbc.query("SELECT * FROM drive_storage_object WHERE object_key=?",(r,n)->new Reference(
                r.getString("object_key"),r.getString("file_id"),r.getString("folder_id"),r.getString("filename"),
                r.getString("media_type"),r.getString("sha256"),r.getLong("expected_size"),r.getString("revision_id")),key);
        return references.isEmpty()?null:references.getFirst();
    }
    private Reference reserve(String key,byte[] bytes,String type,String name) {
        return reservations.execute(status->{
            Reference reference=find(key);
            if(reference==null) {
                String file=drive.generateId();
                jdbc.update("INSERT INTO drive_storage_object(object_key,file_id,folder_id,filename,media_type,sha256,expected_size) VALUES (?,?,?,?,?,?,?) ON CONFLICT(object_key) DO NOTHING",
                        key,file,folder,name,type,FileInspection.sha256(bytes),bytes.length);
                reference=find(key);
            }
            if(!reference.folder().equals(folder)||!reference.type().equals(type)||reference.size()!=bytes.length||!reference.sha().equals(FileInspection.sha256(bytes)))
                throw new ApiException(409,"OBJECT_CONFLICT","La reserva ya corresponde a otro archivo o carpeta.");
            return reference;
        });
    }
    private JsonNode checkFile(Reference reference) {
        var file=drive.file(reference.file());
        if(file.path("trashed").asBoolean(false)||file.path("mimeType").asString().startsWith("application/vnd.google-apps.")||
                !file.path("appProperties").path("atoStorageKey").asString().equals(reference.key())||
                file.path("parents").size()!=1||!file.path("parents").path(0).asString().equals(reference.folder()))
            throw DriveApi.unavailable();
        drive.privatePermissions(reference.file());return file;
    }
    private byte[] verifiedBytes(Reference reference,String revision) {
        var metadata=drive.revision(reference.file(),revision);
        if(!metadata.path("keepForever").asBoolean(false)||!metadata.path("mimeType").asString().equals(reference.type()))
            throw new ApiException(503,"STORAGE_REVISION_REQUIRED","La revisión de evidencia debe permanecer fijada en Drive.");
        byte[] bytes=drive.download(reference.file(),revision,reference.size());
        if(bytes.length!=reference.size()||!FileInspection.sha256(bytes).equals(reference.sha()))
            throw new ApiException(503,"OBJECT_INTEGRITY_FAILURE","La evidencia no pasó la verificación de integridad.");
        return bytes;
    }
    public String put(String key,byte[] bytes,String type) {
        String extension=switch(type){case "application/xml"->".xml";case "application/pdf"->".pdf";case "image/png"->".png";case "image/jpeg"->".jpg";default->throw DriveApi.unavailable();};
        return put(key,bytes,type,key.substring(key.lastIndexOf('/')+1)+extension);
    }
    @Override public String put(String key,byte[] bytes,String type,String filename) {
        ready();
        if(key==null||key.length()>120||!key.matches("business/[A-Za-z0-9_/-]+")||bytes.length==0||bytes.length>FileInspection.limit(type))
            throw DriveApi.unavailable();
        checkFolder();
        // UUID conserva unicidad/identificación; el nombre original ayuda a la operación en Drive.
        String name=key.substring(key.lastIndexOf('/')+1)+" — "+filename;
        if(name.length()>220)throw DriveApi.unavailable();
        Reference reference=reserve(key,bytes,type,name);
        if(reference.revision()!=null) {
            get(key,reference.token(),bytes.length);return reference.token();
        }
        drive.create(reference.file(),reference.folder(),reference.key(),reference.name(),bytes,reference.type());
        String revision=checkFile(reference).path("headRevisionId").asString();
        drive.pin(reference.file(),revision);
        verifiedBytes(reference,revision);
        return reservations.execute(status->{
            jdbc.update("UPDATE drive_storage_object SET revision_id=? WHERE object_key=? AND revision_id IS NULL",revision,key);
            var committed=find(key);
            if(!revision.equals(committed.revision()))throw DriveApi.unavailable();
            return committed.token();
        });
    }
    public Stored get(String key,String version,long limit) {
        ready();
        Reference reference=find(key);
        if(reference==null||reference.revision()==null||!reference.folder().equals(folder)||limit<reference.size()||
                !reference.token().equals(version))throw DriveApi.unavailable();
        checkFolder();checkFile(reference);
        return new Stored(verifiedBytes(reference,reference.revision()),reference.token());
    }
    public void delete(String key,String version) {
        throw new ApiException(409,"RETENTION_POLICY_REQUIRED","La eliminación de evidencias requiere una política aprobada.");
    }
}
