package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.documents.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Servidor opt-in de pruebas: PostgreSQL real y OIDC real; archivos/antivirus controlados.
 * Solo en test-classes, nunca se incluye en el JAR de aplicación. */
public final class M2BrowserHarness {
    @TestConfiguration public static class Adapters {
        @Bean @Primary ObjectStoragePort testStorage() {return new ObjectStoragePort() {
            private final Map<String,Stored> objects=new ConcurrentHashMap<>();
            private final Path folder="m4".equals(System.getenv("ATO_BROWSER_MILESTONE"))?Path.of(System.getenv().getOrDefault("ATO_BROWSER_OBJECTS_FOLDER","target/m4-browser-objects")):null;
            private Path path(String key,String version){return folder.resolve(com.daraya.atoconstrucciones.shared.IdempotencyService.hash(key)+"-"+version+".bin");}
            public String put(String key,byte[] bytes,String type){String version=UUID.randomUUID().toString();objects.put(key+":"+version,new Stored(bytes.clone(),version));
                if(folder!=null)try{Files.createDirectories(folder);Files.write(path(key,version),bytes);}catch(Exception e){throw new IllegalStateException(e);}return version;}
            public Stored get(String key,String version,long limit){var value=objects.get(key+":"+version);if(value==null&&folder!=null)try{value=new Stored(Files.readAllBytes(path(key,version)),version);}catch(Exception e){throw new IllegalStateException(e);}
                if(value==null)throw new IllegalStateException("Missing test object");return value;}
            public void delete(String key,String version){objects.remove(key+":"+version);}
            public boolean available(){return true;}
        };}
        @Bean @Primary ScannerPort testScanner(){return bytes->ScannerPort.Result.CLEAN;}
    }
    private M2BrowserHarness() {}
    public static void main(String[] args)throws Exception {
        System.setProperty("spring.devtools.restart.enabled","false");
        if(!"1".equals(System.getenv("ATO_M2_BROWSER_HARNESS")))throw new IllegalStateException("Test harness requires explicit opt-in");
        var pg=EmbeddedPostgres.builder().setPort(0).start();
        System.setProperty("spring.datasource.url",pg.getJdbcUrl("postgres","postgres"));System.setProperty("spring.datasource.username","postgres");System.setProperty("spring.datasource.password","");
        System.setProperty("spring.profiles.active","dev");System.setProperty("server.port","9192");System.setProperty("ato.jobs.enabled","false");System.setProperty("ato.storage.enabled","false");System.setProperty("ato.documents.worker-enabled","true");System.setProperty("ato.documents.poll-ms","150");
        System.setProperty("logging.level.org.springframework.jdbc","INFO");
        String milestone=Set.of("m3","m4").contains(System.getenv("ATO_BROWSER_MILESTONE"))?System.getenv("ATO_BROWSER_MILESTONE"):"m2";
        var app=new SpringApplication(AtoconstruccionesApplication.class,Adapters.class);var context=app.run(args);
        var datasource=context.getBean(javax.sql.DataSource.class);
        try(var connection=datasource.getConnection()) {
            if(!pg.getJdbcUrl("postgres","postgres").equals(connection.getMetaData().getURL()))throw new IllegalStateException("Browser harness datasource differs from backup datasource");
        }
        Files.writeString(Path.of("/tmp/ato-"+milestone+"-browser-db.json"),"{\"port\":"+pg.getPort()+"}");
        Runtime.getRuntime().addShutdownHook(new Thread(()->{context.close();try{pg.close();}catch(Exception ignored){}}));
    }
}
