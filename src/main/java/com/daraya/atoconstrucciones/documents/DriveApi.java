package com.daraya.atoconstrucciones.documents;

import com.daraya.atoconstrucciones.shared.ApiException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Drive API v3: origen fijo en producción, sin redirecciones ni cuerpos sin límite. */
final class DriveApi {
    @FunctionalInterface interface AccessToken { String value() throws IOException; }
    private final URI origin;
    private final AccessToken tokens;
    private final JsonMapper json;
    final boolean requiresSharedDrive;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    DriveApi(JsonMapper json, AccessToken tokens, boolean requiresSharedDrive) {
        this(URI.create("https://www.googleapis.com"),json,tokens,requiresSharedDrive);
    }
    // Origen de fixture HTTP inyectado únicamente por tests, nunca por configuración de producción.
    DriveApi(URI origin, JsonMapper json, AccessToken tokens) { this(origin,json,tokens,false); }
    DriveApi(URI origin, JsonMapper json, AccessToken tokens,boolean requiresSharedDrive) {
        this.origin=origin;this.json=json;this.tokens=tokens;this.requiresSharedDrive=requiresSharedDrive;
    }
    static ApiException unavailable() { return new ApiException(503,"STORAGE_UNAVAILABLE","No se pudo verificar el archivo privado en Google Drive."); }
    private String id(String value) {
        if(value==null||!value.matches("[A-Za-z0-9_-]{1,100}"))throw unavailable();
        return value;
    }
    private byte[] request(String method,String path,String type,byte[] body,long limit,boolean allowConflict) {
        CompletableFuture<HttpResponse<byte[]>> pending=null;
        try {
            var builder=HttpRequest.newBuilder(origin.resolve(path)).timeout(Duration.ofSeconds(25))
                    .header("Authorization","Bearer "+tokens.value()).header("Accept","application/json");
            if(type!=null)builder.header("Content-Type",type);
            builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofByteArray(body));
            pending=http.sendAsync(builder.build(),info->new LimitedBody(info.statusCode()>=400?65536:limit));
            var response=pending.get(30,TimeUnit.SECONDS);
            if(allowConflict&&response.statusCode()==409)return null;
            if(response.statusCode()==401||response.statusCode()==403)
                throw new ApiException(503,"STORAGE_ACCESS_DENIED","Revise la conexión y los permisos de Google Drive.");
            if(response.statusCode()<200||response.statusCode()>=300)throw unavailable();
            return response.body();
        } catch(InterruptedException e) { if(pending!=null)pending.cancel(true);Thread.currentThread().interrupt();throw unavailable(); }
        catch(ApiException e) { throw e; }
        catch(Exception e) { if(pending!=null)pending.cancel(true);throw unavailable(); }
    }
    private JsonNode metadata(String path) {
        try {
            var result=json.readTree(request("GET",path,null,null,1024*1024,false));
            if(!result.isObject())throw unavailable();
            return result;
        } catch(ApiException e) { throw e; }
        catch(Exception e) { throw unavailable(); }
    }
    JsonNode file(String file) {
        return metadata("/drive/v3/files/"+id(file)+"?supportsAllDrives=true&fields=id,mimeType,parents,trashed,headRevisionId,driveId,appProperties,capabilities");
    }
    String generateId() { return id(metadata("/drive/v3/files/generateIds?count=1&type=files").path("ids").path(0).asString()); }
    void privatePermissions(String file) {
        String page="";
        for(int n=0;n<20;n++) {
            var response=metadata("/drive/v3/files/"+id(file)+"/permissions?supportsAllDrives=true&pageSize=100&fields=nextPageToken,permissions(type)"+page);
            if(!response.path("permissions").isArray())throw unavailable();
            for(var permission:response.path("permissions")) {
                String type=permission.path("type").asString("");
                if(Set.of("anyone","domain").contains(type))
                    throw new ApiException(503,"STORAGE_SHARING_UNSAFE","La carpeta de evidencias debe tener acceso restringido.");
                if(!Set.of("user","group").contains(type))throw unavailable();
            }
            String next=response.path("nextPageToken").asString("");
            if(next.isEmpty())return;
            page="&pageToken="+java.net.URLEncoder.encode(next,StandardCharsets.UTF_8);
        }
        throw unavailable();
    }
    void create(String file,String folder,String key,String name,byte[] bytes,String type) {
        String boundary="ato_"+UUID.randomUUID();
        byte[] metadata=json.writeValueAsBytes(Map.of("id",id(file),"name",name,"mimeType",type,
                "parents",List.of(id(folder)),"appProperties",Map.of("atoStorageKey",key)));
        try {
            var body=new ByteArrayOutputStream();
            body.write(("--"+boundary+"\r\nContent-Type: application/json; charset=UTF-8\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(metadata);
            body.write(("\r\n--"+boundary+"\r\nContent-Type: "+type+"\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            body.write(bytes);body.write(("\r\n--"+boundary+"--\r\n").getBytes(StandardCharsets.UTF_8));
            request("POST","/upload/drive/v3/files?uploadType=multipart&supportsAllDrives=true&keepRevisionForever=true&ignoreDefaultVisibility=true",
                    "multipart/related; boundary="+boundary,body.toByteArray(),1024*1024,true);
        } catch(IOException e) { throw unavailable(); }
    }
    JsonNode revision(String file,String revision) {
        return metadata("/drive/v3/files/"+id(file)+"/revisions/"+id(revision)+"?fields=id,keepForever,mimeType");
    }
    void pin(String file,String revision) {
        request("PATCH","/drive/v3/files/"+id(file)+"/revisions/"+id(revision)+"?fields=id,keepForever",
                "application/json",json.writeValueAsBytes(Map.of("keepForever",true)),1024*1024,false);
    }
    byte[] download(String file,String revision,long limit) {
        return request("GET","/drive/v3/files/"+id(file)+"/revisions/"+id(revision)+"?alt=media",null,null,limit,false);
    }
    /** Cancela antes de acumular más bytes que los permitidos, también en JSON/errores. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final long limit;
        private final ByteArrayOutputStream body=new ByteArrayOutputStream();
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private Flow.Subscription subscription;
        LimitedBody(long limit) { this.limit=limit; }
        public CompletionStage<byte[]> getBody() { return result; }
        public void onSubscribe(Flow.Subscription subscription) { this.subscription=subscription;subscription.request(1); }
        public void onNext(List<ByteBuffer> buffers) {
            for(var buffer:buffers) {
                if((long)body.size()+buffer.remaining()>limit) {
                    subscription.cancel();result.completeExceptionally(new IOException("Response limit"));return;
                }
                byte[] bytes=new byte[buffer.remaining()];buffer.get(bytes);body.writeBytes(bytes);
            }
            subscription.request(1);
        }
        public void onError(Throwable error) { result.completeExceptionally(error); }
        public void onComplete() { result.complete(body.toByteArray()); }
    }
}
