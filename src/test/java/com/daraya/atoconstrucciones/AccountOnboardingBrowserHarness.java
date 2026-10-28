package com.daraya.atoconstrucciones;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.springframework.boot.SpringApplication;
import java.nio.file.*;

/** PostgreSQL aislado para altas reales; únicamente en test-classes. */
public final class AccountOnboardingBrowserHarness {
    public static void main(String[] args)throws Exception{
        if(!"1".equals(System.getenv("ATO_ONBOARDING_BROWSER_HARNESS")))throw new IllegalStateException("Explicit opt-in required");
        var pg=EmbeddedPostgres.builder().setPort(0).start();
        System.setProperty("spring.datasource.url",pg.getJdbcUrl("postgres","postgres"));System.setProperty("spring.datasource.username","postgres");System.setProperty("spring.datasource.password","");
        System.setProperty("spring.devtools.restart.enabled","false");System.setProperty("spring.profiles.active","dev");System.setProperty("server.port","9194");
        System.setProperty("ato.jobs.enabled","false");System.setProperty("ato.documents.worker-enabled","false");System.setProperty("ato.contact.worker-enabled","false");System.setProperty("ato.accounts.activation-poll-ms","200");
        var context=SpringApplication.run(AtoconstruccionesApplication.class,args);
        Files.writeString(Path.of(".m0-local/onboarding-isolated/database.json"),"{\"port\":"+pg.getPort()+"}");
        Runtime.getRuntime().addShutdownHook(new Thread(()->{context.close();try{pg.close();}catch(Exception ignored){}}));
    }
}
