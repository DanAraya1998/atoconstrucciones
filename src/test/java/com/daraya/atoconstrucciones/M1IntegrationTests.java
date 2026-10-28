package com.daraya.atoconstrucciones;
import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.parties.PartyService;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.contracts.ContractService;
import com.daraya.atoconstrucciones.documents.*;
import com.daraya.atoconstrucciones.shared.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import java.io.*;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ato.jobs.enabled=false","ato.documents.worker-enabled=false","ATO_DB_PASSWORD=synthetic","ATO_OIDC_CLIENT_SECRET=synthetic","ATO_WEB_ORIGIN=http://localhost:3000","ATO_OIDC_ISSUER_URI=http://idp.example.invalid/realms/ato"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import({M0IntegrationTests.OidcTestConfiguration.class,M1IntegrationTests.Adapters.class})
class M1IntegrationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->M0IntegrationTests.PG.getJdbcUrl("postgres","postgres"));p.add("spring.datasource.username",()->"postgres");}
    static class MemoryStorage implements ObjectStoragePort {
        final Map<String,Stored> objects=new ConcurrentHashMap<>();boolean corrupt;
        public String put(String key,byte[] bytes,String type){String version=UUID.randomUUID().toString();objects.put(key+":"+version,new Stored(bytes.clone(),version));return version;}
        public Stored get(String key,String version,long limit){var obj=objects.get(key+":"+version);if(obj==null)throw new ApiException(503,"STORAGE_UNAVAILABLE","Objeto no disponible.");return corrupt?new Stored(new byte[]{0},version):obj;}
        public void delete(String key,String version){objects.remove(key+":"+version);}
        public boolean available(){return true;}
    }
    static class ControlledScanner implements ScannerPort {
        boolean unavailable,infected;public Result scan(byte[] bytes){if(unavailable)throw new ApiException(503,"SCANNER_UNAVAILABLE","Pendiente.");return infected?Result.INFECTED:Result.CLEAN;}
    }
    @TestConfiguration static class Adapters {
        @Bean @Primary MemoryStorage storage(){return new MemoryStorage();}
        @Bean @Primary ControlledScanner scanner(){return new ControlledScanner();}
    }
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired PartyService parties;@Autowired ProjectService projects;
    @Autowired ContractService contracts;@Autowired DocumentService documents;@Autowired PrivacyService privacy;
    @Autowired MemoryStorage storage;@Autowired ControlledScanner scanner;
    @BeforeEach void reset(){scanner.unavailable=false;scanner.infected=false;storage.corrupt=false;jdbc.update("UPDATE document SET state='REJECTED' WHERE state='QUARANTINED'");}
    String key(){return UUID.randomUUID().toString();}
    String etag(long version){return "\""+version+"\"";}
    Actor actor(String... roles){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO user_account(id,issuer,subject,email) VALUES (?,?,?,?)",id,M0IntegrationTests.ISSUER,id.toString(),id+"@example.invalid");for(String role:roles)jdbc.update("INSERT INTO user_role(user_id,role)VALUES (?,?)",id,role);return new Actor(id,id+"@example.invalid",Set.of(roles),false,Instant.now());}
    RequestPostProcessor login(Actor a){return oidcLogin().idToken(t->t.subject(a.id().toString()).issuer(M0IntegrationTests.ISSUER).claim("email",a.email()).claim("email_verified",true).claim("amr",List.of("pwd")).claim("auth_time",Instant.now()));}
    record Fixture(Actor manager,Actor client,PartyService.Party party,ProjectService.Project project){}
    Fixture fixture(){Actor m=actor("GERENTE"),c=actor("CLIENTE");var party=parties.create(m,"CLIENT",key(),new PartyService.Input("Cliente de prueba",c.email(),"+506 2000 0000","",true));party=parties.member(m,party.id(),"CLIENT",key(),etag(party.version()),new PartyService.Member(c.id(),true));
        var project=projects.create(m,key(),new ProjectService.Input("OB-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),"Obra de prueba",party.id(),"Ubicación sintética",null,null,"obra@example.invalid",LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)));
        project=projects.member(m,project.id(),key(),etag(project.version()),new ProjectService.Member(c.id(),"CLIENT",null,true,true));return new Fixture(m,c,party,project);
    }
    byte[] image(){try{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}catch(IOException e){throw new IllegalStateException(e);}}
    UUID evidence(Fixture f,Actor owner,String clazz){byte[] bytes=image();var session=documents.reserve(owner,key(),new DocumentService.Reservation(f.project().id(),"evidencia.png","image/png",bytes.length,FileInspection.sha256(bytes),clazz));documents.upload(owner,session.document().id(),new ByteArrayInputStream(bytes),bytes.length);documents.complete(owner,session.document().id(),key());assertThat(documents.scanNext()).isTrue();assertThat(documents.read(owner,session.document().id()).state()).isEqualTo("CLEAN");return session.document().id();}
    ContractService.Contract base(Fixture f,UUID doc){var c=contracts.save(f.manager(),f.project().id(),key(),etag(f.project().version()),new ContractService.Input("CON-"+key(),"Contrato de prueba",new Money("1000000.00","CRC"),new Money("130000.00","CRC"),LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)));return contracts.formalize(f.manager(),f.project().id(),key(),etag(c.version()),new ContractService.Evidence(doc,"Contrato documentado"));}
    ContractService.Extra quoted(Fixture f){var e=contracts.request(f.client(),f.project().id(),key(),new ContractService.Request("Trabajo adicional"));return contracts.revise(f.manager(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Revision("Alcance versión 1",new Money("200000.10","CRC"),new Money("26000.01","CRC"),3));}
    @Test void legacyS3EvidenceRemainsIdentifiableAndCannotBeReadAsDrive(){
        var f=fixture();UUID id=evidence(f,f.manager(),"EVIDENCE");
        assertThat(jdbc.queryForObject("SELECT storage_provider FROM document WHERE id=?",String.class,id)).isEqualTo("GOOGLE_DRIVE");
        jdbc.update("UPDATE document SET storage_provider='S3' WHERE id=?",id);
        assertThat(documents.read(f.manager(),id).state()).isEqualTo("CLEAN");
        assertThatThrownBy(()->documents.download(f.manager(),id)).isInstanceOfSatisfying(ApiException.class,error->assertThat(error.code()).isEqualTo("STORAGE_MIGRATION_REQUIRED"));
    }
    @Test void rbacFiltersPartiesProjectsChildrenAndSupplierProjection()throws Exception{
        var f=fixture();var other=fixture();Actor assistant=actor("ASISTENTE_ADMINISTRATIVA"),supplier=actor("PROVEEDOR"),admin=actor("ADMIN");
        var s=parties.create(f.manager(),"SUPPLIER",key(),new PartyService.Input("Proveedor sintético",supplier.email(),"+506 2000 0000","Servicios",true));s=parties.member(f.manager(),s.id(),"SUPPLIER",key(),etag(s.version()),new PartyService.Member(supplier.id(),true));
        var p=projects.member(f.manager(),f.project().id(),key(),etag(f.project().version()),new ProjectService.Member(assistant.id(),"OPERATIONS",null,false,true));p=projects.member(f.manager(),p.id(),key(),etag(p.version()),new ProjectService.Member(supplier.id(),"SUPPLIER",s.id(),false,true));
        for(Actor a:List.of(f.client(),assistant,supplier)){
            mvc.perform(get("/api/v1/projects/"+p.id()).with(login(a))).andExpect(status().isOk());mvc.perform(get("/api/v1/projects/"+other.project().id()).with(login(a))).andExpect(status().isNotFound());mvc.perform(get("/api/v1/projects").with(login(a))).andExpect(jsonPath("$.items.length()").value(1));
        }
        mvc.perform(get("/api/v1/projects/"+p.id()).with(login(supplier))).andExpect(jsonPath("$.clientId").doesNotExist()).andExpect(jsonPath("$.contactEmail").doesNotExist());
        mvc.perform(get("/api/v1/projects/"+p.id()+"/contract").with(login(supplier))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects/"+p.id()+"/extras").with(login(supplier))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects").with(login(admin))).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/clients").with(login(f.client()))).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].email").doesNotExist());
        parties.member(f.manager(),f.party().id(),"CLIENT",key(),etag(f.party().version()),new PartyService.Member(f.client().id(),false));
        mvc.perform(get("/api/v1/projects/"+p.id()).with(login(f.client()))).andExpect(status().isNotFound());
    }
    @Test void projectCodeRequiresCanonicalFormatAndKeepsUniqueness()throws Exception{
        var manager=actor("GERENTE");var party=parties.create(manager,"CLIENT",key(),new PartyService.Input("Cliente de prueba","client@example.invalid","20000000","",true));
        String requestKey=key();
        String body="""
            {"code":"%s","name":"Obra de prueba","clientId":"%s","location":"Ubicación sintética","contactEmail":"project@example.invalid","startsOn":"2026-10-15","estimatedEndsOn":"2027-01-15"}
            """;
        for(String code:List.of("Prueba_001","prueba-001","OB","OB 001","OB@001","A".repeat(31))){
            mvc.perform(post("/api/v1/projects").with(login(manager)).with(csrf()).header("Idempotency-Key",requestKey).contentType("application/json").content(body.formatted(code,party.id())))
                .andExpect(status().is(422)).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project WHERE client_id=?",Integer.class,party.id())).isZero();
        mvc.perform(post("/api/v1/projects").with(login(manager)).with(csrf()).header("Idempotency-Key",requestKey).contentType("application/json").content(body.formatted("PRUEBA-001",party.id())))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.code").value("PRUEBA-001")).andExpect(jsonPath("$.state").value("BORRADOR"));
        mvc.perform(post("/api/v1/projects").with(login(manager)).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body.formatted("PRUEBA-001",party.id())))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("DUPLICATE_CODE"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project WHERE client_id=?",Integer.class,party.id())).isEqualTo(1);
    }
    @Test void validatesContactsDatesCoordinatesUnknownFieldsAndInactiveClient()throws Exception{
        var f=fixture();String body="{\"code\":\"INVALID\",\"name\":\"Obra\",\"clientId\":\""+f.party().id()+"\",\"location\":\"X\",\"contactEmail\":\"invalid\",\"startsOn\":\"2026-12-01\",\"estimatedEndsOn\":\"2026-10-01\"}";
        mvc.perform(post("/api/v1/projects").with(login(f.manager())).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().is(422));
        mvc.perform(post("/api/v1/clients").with(login(f.manager())).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content("{\"name\":\"X\",\"email\":\"x@example.invalid\",\"phone\":\"-------\",\"category\":\"\",\"active\":true}")).andExpect(status().is(422));
        assertThatThrownBy(()->projects.create(f.manager(),key(),new ProjectService.Input("BAD-DATE","X",f.party().id(),"X",null,null,"x@example.invalid",LocalDate.of(2026,12,1),LocalDate.of(2026,10,1)))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->projects.edit(f.manager(),f.project().id(),key(),etag(f.project().version()),new ProjectService.Edit("X","X",new java.math.BigDecimal("91"),new java.math.BigDecimal("0"),"x@example.invalid",LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)))).isInstanceOf(ApiException.class);
        parties.edit(f.manager(),f.party().id(),"CLIENT",key(),etag(f.party().version()),new PartyService.Input("Inactivo","x@example.invalid","+506 2000 0000","",false));
        assertThatThrownBy(()->projects.create(f.manager(),key(),new ProjectService.Input("INACTIVE","X",f.party().id(),"X",null,null,"x@example.invalid",LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)))).isInstanceOf(ApiException.class);
        mvc.perform(post("/api/v1/clients").with(login(f.manager())).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content("{\"name\":\"X\",\"email\":\"x@example.invalid\",\"phone\":\"20000000\",\"category\":\"\",\"active\":true,\"role\":\"GERENTE\"}")).andExpect(status().isBadRequest());
    }
    @Test void activationNeedsContractEvidenceAndRepresentativeAndKeepsHistory(){
        var f=fixture();assertThatThrownBy(()->projects.transition(f.manager(),f.project().id(),key(),etag(f.project().version()),"activate","")).isInstanceOf(ApiException.class);
        UUID doc=evidence(f,f.manager(),"CONTRACT");base(f,doc);var p=projects.transition(f.manager(),f.project().id(),key(),etag(f.project().version()),"activate","Inicio autorizado");assertThat(p.state()).isEqualTo("ACTIVO");
        p=projects.transition(f.manager(),p.id(),key(),etag(p.version()),"suspend","Pausa acordada");assertThat(p.state()).isEqualTo("SUSPENDIDO");
        final var paused=p;assertThatThrownBy(()->projects.transition(f.manager(),paused.id(),key(),etag(paused.version()),"cancel","Cancelar")).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_hold WHERE document_id=?",Integer.class,doc)).isEqualTo(1);
        assertThatThrownBy(()->jdbc.update("UPDATE contract SET net_amount=0 WHERE project_id=?",f.project().id())).isInstanceOf(RuntimeException.class);
    }
    @Test void pendingExtraDoesNotChangeContractAndCurrentRevisionAcceptanceIsRequired(){
        var f=fixture();UUID doc=evidence(f,f.manager(),"CONTRACT");base(f,doc);var e=quoted(f);
        assertThat(contracts.read(f.manager(),f.project().id()).currentTotal().amount()).isEqualTo("1130000.00");
        UUID acceptance=evidence(f,f.client(),"EVIDENCE");e=contracts.accept(f.client(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Evidence(acceptance,"Acepto versión uno"));
        e=contracts.revise(f.manager(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Revision("Alcance versión 2",new Money("300000.00","CRC"),new Money("0.00","CRC"),5));final var revision=e;
        assertThatThrownBy(()->contracts.approve(f.manager(),f.project().id(),revision.id(),key(),etag(revision.version()),new ContractService.Evidence(doc,"Formalización"))).isInstanceOf(ApiException.class);
        e=contracts.accept(f.manager(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Evidence(doc,"Aceptación documental nueva"));
        e=contracts.approve(f.manager(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Evidence(doc,"Aprobado"));assertThat(e.revisions()).hasSize(2);
        assertThat(contracts.read(f.client(),f.project().id()).currentTotal().amount()).isEqualTo("1430000.00");
    }
    @Test void concurrentApprovalsHaveOneAmendmentAndCancellationIsLinkedDelta()throws Exception{
        var f=fixture();UUID doc=evidence(f,f.manager(),"CONTRACT");base(f,doc);var e=quoted(f);e=contracts.accept(f.manager(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Evidence(doc,"Aceptación externa documentada"));
        final var pending=e;String key=key();var input=new ContractService.Evidence(doc,"Formalizado");
        try(var executor=Executors.newVirtualThreadPerTaskExecutor()){
            var futures=new ArrayList<Future<ContractService.Extra>>();for(int i=0;i<8;i++)futures.add(executor.submit(()->contracts.approve(f.manager(),f.project().id(),pending.id(),key,etag(pending.version()),input)));
            for(var future:futures)assertThat(future.get(15,TimeUnit.SECONDS).state()).isEqualTo("APROBADO");
        }
        var c=contracts.read(f.manager(),f.project().id());assertThat(c.currentTotal().amount()).isEqualTo("1356000.11");assertThat(c.amendments()).hasSize(1);
        var approved=contracts.readExtra(f.manager(),f.project().id(),e.id());var cancelled=contracts.cancel(f.manager(),f.project().id(),e.id(),key(),etag(approved.version()),new ContractService.Adjustment(new Money("-100000.00","CRC"),new Money("-13000.00","CRC"),-1,doc,"Se conserva el trabajo reconocido y se retira el resto"));assertThat(cancelled.state()).isEqualTo("CANCELADO");
        c=contracts.read(f.manager(),f.project().id());assertThat(c.currentTotal().amount()).isEqualTo("1243000.11");assertThat(c.amendments()).hasSize(2);assertThat(c.amendments().getLast().reversalOf()).isEqualTo(c.amendments().getFirst().id());
        assertThatThrownBy(()->jdbc.update("DELETE FROM contract_amendment WHERE project_id=?",f.project().id())).isInstanceOf(RuntimeException.class);
    }
    @Test void moneyRejectsCurrencyScaleAndNegativeContractAndAmendmentCannotMakeTotalNegative(){
        var f=fixture();UUID doc=evidence(f,f.manager(),"CONTRACT");base(f,doc);
        for(Money amount:List.of(new Money("1.001","CRC"),new Money("-1.00","CRC"),new Money("1.00","USD")))assertThatThrownBy(()->contracts.revise(f.manager(),f.project().id(),contracts.request(f.manager(),f.project().id(),key(),new ContractService.Request("Extra")).id(),key(),"\"1\"",new ContractService.Revision("X",amount,new Money("0.00","CRC"),0))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->contracts.adjust(f.manager(),f.project().id(),key(),etag(contracts.read(f.manager(),f.project().id()).version()),new ContractService.Adjustment(new Money("-2000000.00","CRC"),new Money("0.00","CRC"),0,doc,"Reducir"))).isInstanceOf(ApiException.class);
    }
    @Test void crossProjectEvidenceAndExtraAndUnauthorizedAcceptanceAreRejected(){
        var f=fixture();var other=fixture();UUID otherDoc=evidence(other,other.manager(),"CONTRACT");var e=quoted(f);
        assertThatThrownBy(()->contracts.readExtra(f.client(),f.project().id(),contracts.request(other.client(),other.project().id(),key(),new ContractService.Request("Ajeno")).id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->contracts.accept(f.client(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Evidence(otherDoc,"Ajena"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->contracts.accept(other.client(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Evidence(otherDoc,"Ajeno"))).isInstanceOf(ApiException.class);
        Actor stale=new Actor(f.manager().id(),f.manager().email(),f.manager().roles(),false,Instant.now().minusSeconds(301));assertThatThrownBy(()->projects.member(stale,f.project().id(),key(),etag(f.project().version()),new ProjectService.Member(f.client().id(),"CLIENT",null,true,true))).isInstanceOf(ApiException.class);
    }
    @Test void documentQuarantineFailureRetryInfectionAndIntegrityAreFailClosed()throws Exception{
        var f=fixture();byte[] bytes=image();var d=documents.reserve(f.manager(),key(),new DocumentService.Reservation(f.project().id(),"scan.png","image/png",bytes.length,FileInspection.sha256(bytes),"EVIDENCE")).document();documents.upload(f.manager(),d.id(),new ByteArrayInputStream(bytes),bytes.length);
        mvc.perform(get("/api/v1/documents/"+d.id()+"/content").with(login(f.manager()))).andExpect(status().isConflict());
        scanner.unavailable=true;documents.scanNext();assertThat(documents.read(f.manager(),d.id()).state()).isEqualTo("QUARANTINED");scanner.unavailable=false;jdbc.update("UPDATE document SET scan_after=now() WHERE id=?",d.id());documents.scanNext();assertThat(documents.download(f.manager(),d.id()).bytes()).isEqualTo(bytes);
        storage.corrupt=true;assertThatThrownBy(()->documents.download(f.manager(),d.id())).isInstanceOf(ApiException.class);storage.corrupt=false;
        var infected=documents.reserve(f.manager(),key(),new DocumentService.Reservation(f.project().id(),"infected.png","image/png",bytes.length,FileInspection.sha256(bytes),"EVIDENCE")).document();documents.upload(f.manager(),infected.id(),new ByteArrayInputStream(bytes),bytes.length);scanner.infected=true;documents.scanNext();assertThat(documents.read(f.manager(),infected.id()).state()).isEqualTo("INFECTED");assertThatThrownBy(()->documents.download(f.manager(),infected.id())).isInstanceOf(ApiException.class);
    }
    @Test void documentAudiencesRevocationAndIncompleteHashProtectDownloads()throws Exception{
        var f=fixture();var other=fixture();UUID doc=evidence(f,f.manager(),"CONTRACT");
        mvc.perform(get("/api/v1/documents/"+doc).with(login(f.client()))).andExpect(status().isNotFound());
        var d=documents.read(f.manager(),doc);documents.share(f.manager(),doc,key(),etag(d.version()),new DocumentService.Sharing("CLIENT",null,"Compartir contrato"));
        mvc.perform(get("/api/v1/documents/"+doc+"/content").with(login(f.client()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"));
        mvc.perform(get("/api/v1/documents/"+doc).with(login(other.client()))).andExpect(status().isNotFound());
        var p=projects.member(f.manager(),f.project().id(),key(),etag(f.project().version()),new ProjectService.Member(f.client().id(),"CLIENT",null,true,false));
        mvc.perform(get("/api/v1/documents/"+doc+"/content").with(login(f.client()))).andExpect(status().isNotFound());
        byte[] bytes=image();var reserved=documents.reserve(f.manager(),key(),new DocumentService.Reservation(p.id(),"bad.png","image/png",bytes.length,"0".repeat(64),"EVIDENCE")).document();
        assertThatThrownBy(()->documents.upload(f.manager(),reserved.id(),new ByteArrayInputStream(bytes),bytes.length)).isInstanceOf(ApiException.class);assertThatThrownBy(()->documents.complete(f.manager(),reserved.id(),key())).isInstanceOf(ApiException.class);
    }
    @Test void etagIdempotencyAndAuditFailureRollBackBusinessCommands(){
        var f=fixture();var edit=new ProjectService.Edit("Nueva ficha","X",null,null,"x@example.invalid",f.project().startsOn(),f.project().estimatedEndsOn());String key=key();var changed=projects.edit(f.manager(),f.project().id(),key,etag(f.project().version()),edit);assertThat(projects.edit(f.manager(),f.project().id(),key,etag(f.project().version()),edit)).isEqualTo(changed);
        assertThatThrownBy(()->projects.edit(f.manager(),f.project().id(),key(),etag(f.project().version()),edit)).isInstanceOf(ApiException.class);
        jdbc.execute("CREATE FUNCTION test_m1_audit_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id='"+f.manager().id()+"'::uuid THEN RAISE EXCEPTION 'synthetic failure'; END IF; RETURN NEW; END; $$");jdbc.execute("CREATE TRIGGER test_m1_audit_failure BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION test_m1_audit_failure()");
        try{assertThatThrownBy(()->contracts.request(f.manager(),f.project().id(),key(),new ContractService.Request("Rollback"))).isInstanceOf(RuntimeException.class);assertThat(jdbc.queryForObject("SELECT count(*) FROM extra_request WHERE project_id=?",Integer.class,f.project().id())).isZero();}
        finally{jdbc.execute("DROP TRIGGER test_m1_audit_failure ON audit_event");jdbc.execute("DROP FUNCTION test_m1_audit_failure()");}
    }
    @Test void privacyDraftGatesConsentAndWithdrawalPreservesDocumentAndHolds(){
        var f=fixture();UUID photo=evidence(f,f.client(),"PHOTO"),proof=evidence(f,f.client(),"EVIDENCE");var notice=privacy.draft(f.manager(),key(),new PrivacyService.NoticeInput("Responsable sintético","privacidad@example.invalid","Texto de prueba; no es el aviso productivo"));
        var input=new PrivacyService.ConsentInput(f.project().id(),photo,notice.id(),proof,"Portafolio","Sitio ATO",LocalDate.of(2027,10,8));
        assertThatThrownBy(()->privacy.grant(f.client(),key(),input)).isInstanceOf(ApiException.class);privacy.approve(f.manager(),notice.id(),key());var c=privacy.grant(f.client(),key(),input);c=privacy.withdraw(f.client(),c.id(),key(),etag(c.version()),"Retiro autorizado");assertThat(c.withdrawnAt()).isNotNull();assertThat(documents.read(f.client(),photo).state()).isEqualTo("CLEAN");assertThat(privacy.retention()).allMatch(r->!r.automaticDeletion());
        var other=fixture();privacy.request(f.client(),key(),new PrivacyService.RequestInput("RECTIFICATION","Corregir contacto"));assertThat(privacy.requests(other.client())).isEmpty();assertThat(privacy.requests(f.client())).hasSize(1);
    }
    @Test void rejectsHostileXmlPdfAndImageAndExpiresOnlyEmptyReservations(){
        String xxe="<?xml version='1.0'?><!DOCTYPE x [<!ENTITY remote SYSTEM 'file:///etc/passwd'>]><x>&remote;</x>";
        assertThatThrownBy(()->FileInspection.inspect(xxe.getBytes(java.nio.charset.StandardCharsets.UTF_8),"application/xml")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->FileInspection.inspect("<html>active</html>".getBytes(),"application/pdf")).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->FileInspection.inspect("%PDF-fake".getBytes(),"image/png")).isInstanceOf(ApiException.class);
        var f=fixture();byte[] bytes=image();var d=documents.reserve(f.manager(),key(),new DocumentService.Reservation(f.project().id(),"pending.png","image/png",bytes.length,FileInspection.sha256(bytes),"EVIDENCE")).document();jdbc.update("UPDATE document SET expires_at=now()-interval '2 hours' WHERE id=?",d.id());documents.expireReservations();assertThat(documents.read(f.manager(),d.id()).state()).isEqualTo("EXPIRED");
    }
    @Test void historicalContractCutUsesOnlyEffectiveAmendments(){
        var f=fixture();UUID doc=evidence(f,f.manager(),"CONTRACT");base(f,doc);Instant cutoff=Instant.now();var e=quoted(f);
        e=contracts.accept(f.manager(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Evidence(doc,"Aceptación externa"));
        contracts.approve(f.manager(),f.project().id(),e.id(),key(),etag(e.version()),new ContractService.Evidence(doc,"Formalizado"));
        assertThat(contracts.read(f.manager(),f.project().id(),cutoff).currentTotal().amount()).isEqualTo("1130000.00");
        assertThat(contracts.read(f.manager(),f.project().id()).currentTotal().amount()).isEqualTo("1356000.11");
        assertThatThrownBy(()->contracts.read(f.manager(),f.project().id(),Instant.now().plusSeconds(60))).isInstanceOf(ApiException.class);
    }
    @Test void approvedNoticeAndWithdrawnConsentCannotLoseTheirHistory(){
        var f=fixture();UUID photo=evidence(f,f.client(),"PHOTO"),proof=evidence(f,f.client(),"EVIDENCE");var n=privacy.draft(f.manager(),key(),new PrivacyService.NoticeInput("Responsable sintético","privacidad@example.invalid","Aviso de prueba"));privacy.approve(f.manager(),n.id(),key());
        assertThatThrownBy(()->jdbc.update("UPDATE privacy_notice SET content='changed' WHERE id=?",n.id())).isInstanceOf(RuntimeException.class);
        var c=privacy.grant(f.client(),key(),new PrivacyService.ConsentInput(f.project().id(),photo,n.id(),proof,"Portafolio","Sitio ATO",LocalDate.of(2027,10,8)));
        final UUID consent=c.id();assertThatThrownBy(()->jdbc.update("UPDATE publication_consent SET purpose='changed' WHERE id=?",consent)).isInstanceOf(RuntimeException.class);
        privacy.withdraw(f.client(),consent,key(),etag(c.version()),"Retirar autorización");assertThatThrownBy(()->jdbc.update("DELETE FROM publication_consent WHERE id=?",consent)).isInstanceOf(RuntimeException.class);
    }
}
