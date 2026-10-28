package com.daraya.atoconstrucciones.documents;

import com.daraya.atoconstrucciones.shared.ApiException;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

/** Protocolo HTTP simulado + PostgreSQL real. No certifica permisos ni operación Google real. */
class GoogleDriveStorageTests {
    static EmbeddedPostgres pg;
    static JdbcTemplate jdbc;
    static DataSourceTransactionManager transactions;
    final JsonMapper json=JsonMapper.builder().build();
    HttpServer server;
    GoogleDriveObjectStorage storage;
    String root;
    final Map<String,RemoteFile> files=new ConcurrentHashMap<>();
    final List<String> requests=new CopyOnWriteArrayList<>();
    final AtomicInteger generated=new AtomicInteger();
    boolean publicFolder,publicFile,revoked,failAfterCreate,corrupt,oversized,redirect,sharedFolder=true;
    private static class RemoteFile {
        String key,name,type,parent,head="revision_1";
        final Map<String,byte[]> revisions=new ConcurrentHashMap<>();
        final Set<String> pinned=ConcurrentHashMap.newKeySet();
    }
    @BeforeAll static void database() throws Exception {
        pg=EmbeddedPostgres.builder().setPort(0).start();
        Flyway.configure().dataSource(pg.getPostgresDatabase()).load().migrate();
        jdbc=new JdbcTemplate(pg.getPostgresDatabase());transactions=new DataSourceTransactionManager(pg.getPostgresDatabase());
    }
    @AfterAll static void closeDatabase() throws Exception { if(pg!=null)pg.close(); }
    @BeforeEach void api() throws Exception {
        root="folder_"+UUID.randomUUID().toString().replace("-","");
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/",exchange->{
            try {
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic");
                String path=exchange.getRequestURI().getPath(),query=Objects.toString(exchange.getRequestURI().getRawQuery(),"");
                String method=exchange.getRequestMethod();requests.add(method+" "+exchange.getRequestURI());
                int status=200;Object response=Map.of();byte[] raw=null;
                if(revoked){status=403;response=Map.of("error","private simulated detail");}
                else if(path.equals("/drive/v3/files/generateIds"))response=Map.of("ids",List.of(root+"_file_"+generated.incrementAndGet()));
                else if(path.equals("/upload/drive/v3/files")) {
                    assertThat(method).isEqualTo("POST");
                    assertThat(query).contains("keepRevisionForever=true","ignoreDefaultVisibility=true","supportsAllDrives=true");
                    String body=new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8);
                    String boundary=exchange.getRequestHeaders().getFirst("Content-Type").split("boundary=")[1];
                    String[] parts=body.split("--"+boundary);
                    var metadata=json.readTree(parts[1].substring(parts[1].indexOf("\r\n\r\n")+4).trim());
                    String id=metadata.path("id").asString();RemoteFile file=new RemoteFile();
                    file.key=metadata.path("appProperties").path("atoStorageKey").asString();
                    file.name=metadata.path("name").asString();file.type=metadata.path("mimeType").asString();file.parent=metadata.path("parents").path(0).asString();
                    String content=parts[2].substring(parts[2].indexOf("\r\n\r\n")+4);content=content.substring(0,content.length()-2);
                    file.revisions.put(file.head,content.getBytes(StandardCharsets.UTF_8));
                    if(files.putIfAbsent(id,file)!=null)status=409;
                    else if(failAfterCreate){failAfterCreate=false;status=503;}
                    response=Map.of("id",id,"headRevisionId",file.head);
                } else if(path.endsWith("/permissions")) {
                    boolean exposed=path.equals("/drive/v3/files/"+root+"/permissions")?publicFolder:publicFile;
                    response=Map.of("permissions",List.of(Map.of("type",exposed?"anyone":"user")));
                } else if(path.equals("/drive/v3/files/"+root)) {
                    response=sharedFolder?Map.of("mimeType","application/vnd.google-apps.folder","trashed",false,"driveId","shared_1"):
                            Map.of("mimeType","application/vnd.google-apps.folder","trashed",false);
                } else if(path.startsWith("/drive/v3/files/")) {
                    String[] components=path.split("/");String id=components[4];RemoteFile file=files.get(id);
                    if(file==null)status=404;
                    else if(components.length==5)response=Map.of("id",id,"mimeType",file.type,"parents",List.of(file.parent),"trashed",false,"headRevisionId",file.head,"appProperties",Map.of("atoStorageKey",file.key));
                    else if(components.length==7&&components[5].equals("revisions")) {
                        String revision=components[6];
                        if(!file.revisions.containsKey(revision))status=404;
                        else if(method.equals("PATCH")){assertThat(json.readTree(exchange.getRequestBody().readAllBytes()).path("keepForever").asBoolean()).isTrue();file.pinned.add(revision);}
                        else if(query.equals("alt=media")) {
                            raw=oversized?new byte[10000]:corrupt?"corrupt".getBytes(StandardCharsets.UTF_8):file.revisions.get(revision);
                            if(redirect){status=302;exchange.getResponseHeaders().set("Location","http://127.0.0.1:1/private");raw=null;}
                        }
                        if(raw==null&&status==200)response=Map.of("id",revision,"keepForever",file.pinned.contains(revision),"mimeType",file.type);
                    } else status=404;
                } else status=404;
                if(raw==null)raw=json.writeValueAsBytes(response);
                exchange.sendResponseHeaders(status,raw.length);exchange.getResponseBody().write(raw);
            } finally { exchange.close(); }
        });
        server.start();
        var drive=new DriveApi(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),json,()->"synthetic");
        storage=new GoogleDriveObjectStorage(drive,root,jdbc,transactions);
    }
    @AfterEach void stop() { if(server!=null)server.stop(0); }
    String key() { return "business/project/"+UUID.randomUUID(); }
    byte[] content() { return "<evidence>original</evidence>".getBytes(StandardCharsets.UTF_8); }
    @Test void uploadPinsOriginalAndReadsPinnedRevisionAfterManualEdit() {
        String key=key();byte[] original=content();String version=storage.put(key,original,"application/xml","evidencia.xml");
        var file=files.values().iterator().next();assertThat(file.name).endsWith(" — evidencia.xml");
        file.head="revision_2";file.revisions.put(file.head,"manual edit".getBytes(StandardCharsets.UTF_8));
        assertThat(storage.get(key,version,original.length).bytes()).isEqualTo(original);
        assertThat(requests).anyMatch(request->request.contains("/revisions/revision_1?alt=media"));
        assertThatThrownBy(()->storage.get(key,"drive:other:revision_1",original.length)).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->storage.delete(key,version)).isInstanceOf(ApiException.class);
        assertThat(requests).noneMatch(request->request.startsWith("DELETE"));
    }
    @Test void remoteCommitWithFailedResponseRetriesUsingSameReservedFile() {
        String key=key();failAfterCreate=true;
        assertThatThrownBy(()->storage.put(key,content(),"application/xml")).isInstanceOf(ApiException.class);
        String version=storage.put(key,content(),"application/xml");
        assertThat(storage.get(key,version,content().length).bytes()).isEqualTo(content());
        assertThat(files).hasSize(1);assertThat(generated.get()).isEqualTo(1);
    }
    @Test void databaseRollbackDoesNotLoseRemoteReferenceOrCreateAnotherFile() {
        String key=key();String[] pinned={null};
        assertThatThrownBy(()->new TransactionTemplate(transactions).executeWithoutResult(status->{
            pinned[0]=storage.put(key,content(),"application/xml");throw new IllegalStateException("synthetic audit rollback");
        })).isInstanceOf(IllegalStateException.class);
        assertThat(storage.put(key,content(),"application/xml")).isEqualTo(pinned[0]);
        assertThat(files).hasSize(1);assertThat(generated.get()).isEqualTo(1);
    }
    @Test void concurrentRetriesHaveOneRemoteFileAndOneRevisionReference() throws Exception {
        String key=key();
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<String>> futures=new ArrayList<>();
            for(int n=0;n<4;n++)futures.add(executor.submit(()->storage.put(key,content(),"application/xml")));
            Set<String> versions=new HashSet<>();for(var future:futures)versions.add(future.get(20,TimeUnit.SECONDS));
            assertThat(versions).hasSize(1);assertThat(files).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM drive_storage_object WHERE object_key=?",Integer.class,key)).isEqualTo(1);
        }
    }
    @Test void conflictingContentNeverOverwritesAnExistingEvidence() {
        String key=key();storage.put(key,content(),"application/xml");
        assertThatThrownBy(()->storage.put(key,"different".getBytes(StandardCharsets.UTF_8),"application/xml"))
                .isInstanceOfSatisfying(ApiException.class,error->assertThat(error.status()).isEqualTo(409));
        assertThat(files).hasSize(1);assertThat(requests.stream().filter(request->request.startsWith("POST ")).count()).isEqualTo(1);
    }
    @Test void publicPermissionsAndRevokedAccessFailClosedWithoutLeakingRemoteErrors() {
        String key=key();publicFolder=true;
        assertThatThrownBy(()->storage.put(key,content(),"application/xml")).isInstanceOfSatisfying(ApiException.class,error->assertThat(error.code()).isEqualTo("STORAGE_SHARING_UNSAFE"));
        assertThat(files).isEmpty();publicFolder=false;String version=storage.put(key,content(),"application/xml");
        publicFile=true;assertThatThrownBy(()->storage.get(key,version,content().length)).isInstanceOf(ApiException.class);
        publicFile=false;revoked=true;
        assertThatThrownBy(()->storage.get(key,version,content().length)).isInstanceOfSatisfying(ApiException.class,error->{
            assertThat(error.status()).isEqualTo(503);assertThat(error.getMessage()).doesNotContain("private simulated detail");
        });
    }
    @Test void oversizedCorruptedUnpinnedOrRedirectedRevisionCannotBeDownloaded() {
        String key=key();String version=storage.put(key,content(),"application/xml");
        corrupt=true;assertThatThrownBy(()->storage.get(key,version,content().length)).isInstanceOf(ApiException.class);corrupt=false;
        oversized=true;assertThatThrownBy(()->storage.get(key,version,content().length)).isInstanceOf(ApiException.class);oversized=false;
        redirect=true;assertThatThrownBy(()->storage.get(key,version,content().length)).isInstanceOf(ApiException.class);redirect=false;
        files.values().iterator().next().pinned.clear();assertThatThrownBy(()->storage.get(key,version,content().length)).isInstanceOf(ApiException.class);
    }
    @Test void disabledOrIncompleteConfigurationDoesNotExposeAStorageFallback() {
        var disabled=new GoogleDriveObjectStorage(false,"","","https://www.googleapis.com/auth/drive.file",jdbc,transactions,json);
        assertThat(disabled.available()).isFalse();assertThatThrownBy(()->disabled.put(key(),content(),"application/xml")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->new GoogleDriveObjectStorage(true,"","","https://www.googleapis.com/auth/drive.file",jdbc,transactions,json)).isInstanceOf(IllegalStateException.class);
    }
    @Test void serviceAccountCannotUploadIntoMyDrive() {
        sharedFolder=false;
        var drive=new DriveApi(URI.create("http://127.0.0.1:"+server.getAddress().getPort()),json,()->"synthetic",true);
        var serviceAccountStorage=new GoogleDriveObjectStorage(drive,root,jdbc,transactions);
        assertThatThrownBy(()->serviceAccountStorage.put(key(),content(),"application/xml"))
                .isInstanceOfSatisfying(ApiException.class,error->assertThat(error.code()).isEqualTo("STORAGE_SHARED_DRIVE_REQUIRED"));
        assertThat(files).isEmpty();
    }
}
