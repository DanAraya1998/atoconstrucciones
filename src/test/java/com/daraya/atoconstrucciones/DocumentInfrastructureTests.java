package com.daraya.atoconstrucciones;
import com.daraya.atoconstrucciones.documents.*;
import com.daraya.atoconstrucciones.parties.PartyService;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import java.util.*;
import java.io.*;
import java.time.*;
import java.nio.charset.StandardCharsets;
import static org.assertj.core.api.Assertions.*;

/** Opt-in: escribe exclusivamente fixtures en la carpeta Drive de pruebas y requiere ClamAV real. */
@EnabledIfEnvironmentVariable(named="ATO_DRIVE_LIVE_TESTS",matches="1")
@SpringBootTest(properties={"ato.jobs.enabled=false","ato.documents.worker-enabled=false","ATO_DB_PASSWORD=synthetic","ATO_OIDC_CLIENT_SECRET=synthetic","ATO_WEB_ORIGIN=http://localhost:3000","ATO_OIDC_ISSUER_URI=http://idp.example.invalid/realms/ato",
 "ato.storage.enabled=true"})
@ActiveProfiles("test") @Import(M0IntegrationTests.OidcTestConfiguration.class)
class DocumentInfrastructureTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->M0IntegrationTests.PG.getJdbcUrl("postgres","postgres"));p.add("spring.datasource.username",()->"postgres");}
    @Autowired DocumentService documents;@Autowired ObjectStoragePort storage;@Autowired ScannerPort scanner;@Autowired ProjectService projects;@Autowired PartyService parties;@Autowired JdbcTemplate jdbc;
    String key(){return UUID.randomUUID().toString();}
    Actor manager(){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO user_account(id,issuer,subject,email) VALUES (?,?,?,?)",id,M0IntegrationTests.ISSUER,id.toString(),id+"@example.invalid");jdbc.update("INSERT INTO user_role(user_id,role)VALUES (?,'GERENTE')",id);return new Actor(id,id+"@example.invalid",Set.of("GERENTE"),false,Instant.now());}
    ProjectService.Project project(Actor actor){var party=parties.create(actor,"CLIENT",key(),new PartyService.Input("Cliente de infraestructura","cliente@example.invalid","20000000","",true));return projects.create(actor,key(),new ProjectService.Input("IF-"+key().substring(0,8).toUpperCase(),"Infraestructura real",party.id(),"Ubicación sintética",null,null,"x@example.invalid",LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)));}
    @Test void realUploadChecksumVersionScanAndPrivateDownloadRoundTrip(){
        Actor actor=manager();var p=project(actor);byte[] bytes="<?xml version='1.0'?><evidencia>Documento sintético</evidencia>".getBytes(StandardCharsets.UTF_8);
        var session=documents.reserve(actor,key(),new DocumentService.Reservation(p.id(),"evidencia.xml","application/xml",bytes.length,FileInspection.sha256(bytes),"EVIDENCE"));
        documents.upload(actor,session.document().id(),new ByteArrayInputStream(bytes),bytes.length);documents.complete(actor,session.document().id(),key());assertThat(documents.scanNext()).isTrue();
        assertThat(documents.read(actor,session.document().id()).state()).isEqualTo("CLEAN");assertThat(documents.download(actor,session.document().id()).bytes()).isEqualTo(bytes);
        assertThat(jdbc.queryForObject("SELECT object_version FROM document WHERE id=?",String.class,session.document().id())).isNotBlank();
    }
    @Test void realAntivirusDetectsEicarAndRefusesDownload(){
        Actor actor=manager();var p=project(actor);
        String signature="X5O!P%@AP[4\\PZX54(P^)7CC)7}$"+"EICAR-STANDARD-ANTIVIRUS-TEST-FILE!$H+H*";
        byte[] bytes=signature.getBytes(StandardCharsets.US_ASCII);
        var d=documents.reserve(actor,key(),new DocumentService.Reservation(p.id(),"antivirus.xml","application/xml",bytes.length,FileInspection.sha256(bytes),"EVIDENCE")).document();
        documents.upload(actor,d.id(),new ByteArrayInputStream(bytes),bytes.length);assertThat(documents.scanNext()).isTrue();assertThat(documents.read(actor,d.id()).state()).isEqualTo("INFECTED");assertThatThrownBy(()->documents.download(actor,d.id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->new ClamAvScanner("127.0.0.1",65534,true,72).scan(bytes)).isInstanceOf(ApiException.class);
    }
    @Test void realRetryAndRestorePreservePinnedChecksum(){
        String object="business/restore/"+key();byte[] original="<evidence>original</evidence>".getBytes(StandardCharsets.UTF_8);
        String pinned=storage.put(object,original,"application/xml");
        assertThat(storage.put(object,original,"application/xml")).isEqualTo(pinned);
        assertThat(storage.get(object,pinned,original.length).bytes()).isEqualTo(original);
        String restored=storage.put(object+"-restored",storage.get(object,pinned,original.length).bytes(),"application/xml");
        assertThat(FileInspection.sha256(storage.get(object+"-restored",restored,original.length).bytes())).isEqualTo(FileInspection.sha256(original));
        assertThatThrownBy(()->storage.put(object,"different".getBytes(StandardCharsets.UTF_8),"application/xml")).isInstanceOf(ApiException.class);
    }
}
