package com.daraya.atoconstrucciones;
import org.springframework.boot.SpringApplication;
import java.nio.file.*;
import tools.jackson.databind.json.JsonMapper;
/** Proceso de smoke sobre una DB restaurada; no crea ni destruye PostgreSQL. Solo test-classes. */
public final class M4RestoredHarness {
    public static void main(String[] args)throws Exception{
        if(!"1".equals(System.getenv("ATO_M2_BROWSER_HARNESS")))throw new IllegalStateException("Explicit test opt-in required");
        var json=new JsonMapper().readTree(Files.readString(Path.of(System.getenv("ATO_BROWSER_RESTORE_ENV"))));
        String database=json.path("database").asString();int port=json.path("port").asInt();if(!database.matches("[a-z0-9_]+")||port<1||port>65535)throw new IllegalStateException("Invalid synthetic restore target");
        System.setProperty("spring.devtools.restart.enabled","false");System.setProperty("spring.datasource.url","jdbc:postgresql://127.0.0.1:"+port+"/"+database);System.setProperty("spring.datasource.username","postgres");System.setProperty("spring.datasource.password","");
        System.setProperty("spring.profiles.active","dev");System.setProperty("server.port",System.getenv().getOrDefault("ATO_BROWSER_RESTORE_PORT","9292"));System.setProperty("ato.jobs.enabled","false");System.setProperty("ato.contact.worker-enabled","false");System.setProperty("ato.documents.worker-enabled","false");System.setProperty("ato.storage.enabled","false");System.setProperty("ato.bootstrap-manager-email","");
        new SpringApplication(AtoconstruccionesApplication.class,M2BrowserHarness.Adapters.class).run(args);
    }
}
