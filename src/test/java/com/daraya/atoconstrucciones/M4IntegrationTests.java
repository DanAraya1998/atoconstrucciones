package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.parties.PartyService;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.documents.*;
import com.daraya.atoconstrucciones.publication.*;
import com.daraya.atoconstrucciones.integrations.mail.ContactMailPort;
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
import tools.jackson.databind.json.JsonMapper;
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

@SpringBootTest(properties={"ato.jobs.enabled=false","ato.contact.worker-enabled=false","ato.documents.worker-enabled=false","ATO_DB_PASSWORD=synthetic","ATO_OIDC_CLIENT_SECRET=synthetic","ATO_WEB_ORIGIN=http://localhost:3000","ATO_OIDC_ISSUER_URI=http://idp.example.invalid/realms/ato"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import({M0IntegrationTests.OidcTestConfiguration.class,M1IntegrationTests.Adapters.class,M4IntegrationTests.MailConfig.class})
class M4IntegrationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->M0IntegrationTests.PG.getJdbcUrl("postgres","postgres"));p.add("spring.datasource.username",()->"postgres");}
    static class Mail implements ContactMailPort {boolean enabled,fail;int sent;public boolean available(){return enabled;}public void notify(UUID id){if(fail)throw new IllegalStateException("Synthetic SMTP failure");sent++;}}
    @TestConfiguration static class MailConfig {@Bean @Primary Mail mail(){return new Mail();}}
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired PartyService parties;@Autowired ProjectService projects;
    @Autowired DocumentService documents;@Autowired PrivacyService privacy;@Autowired PublicationService publications;@Autowired SiteContentService site;
    @Autowired ContactService contacts;@Autowired ContactDeliveryService delivery;@Autowired ContactRateLimiter rate;@Autowired Mail mail;
    @Autowired JsonMapper json;@Autowired M1IntegrationTests.MemoryStorage storage;@Autowired M1IntegrationTests.ControlledScanner scanner;
    @BeforeEach void reset(){mail.enabled=false;mail.fail=false;mail.sent=0;scanner.infected=false;scanner.unavailable=false;storage.corrupt=false;jdbc.update("DELETE FROM contact_rate_limit");jdbc.update("UPDATE contact_delivery SET state='DEAD' WHERE state IN ('PENDING','RUNNING')");jdbc.update("UPDATE document SET state='REJECTED' WHERE state='QUARANTINED'");}
    String key(){return UUID.randomUUID().toString();}String etag(long v){return "\""+v+"\"";}
    Actor actor(String... roles){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO user_account(id,issuer,subject,email) VALUES (?,?,?,?)",id,M0IntegrationTests.ISSUER,id.toString(),id+"@example.invalid");for(String role:roles)jdbc.update("INSERT INTO user_role(user_id,role)VALUES (?,?)",id,role);return new Actor(id,id+"@example.invalid",Set.of(roles),false,Instant.now());}
    RequestPostProcessor login(Actor a){return oidcLogin().idToken(t->t.subject(a.id().toString()).issuer(M0IntegrationTests.ISSUER).claim("email",a.email()).claim("email_verified",true).claim("amr",List.of("pwd")).claim("auth_time",Instant.now()));}
    record Fixture(Actor manager,Actor client,Actor assistant,UUID project,UUID photo,UUID evidence,PrivacyService.Consent consent){}
    Fixture fixture(){
        Actor manager=actor("GERENTE"),client=actor("CLIENTE"),assistant=actor("ASISTENTE_ADMINISTRATIVA");
        var party=parties.create(manager,"CLIENT",key(),new PartyService.Input("PRIVATE-CLIENT",client.email(),"20000000","",true));party=parties.member(manager,party.id(),"CLIENT",key(),etag(party.version()),new PartyService.Member(client.id(),true));
        var p=projects.create(manager,key(),new ProjectService.Input("M4-"+key().substring(0,8).toUpperCase(),"PRIVATE-PROJECT",party.id(),"PRIVATE-ADDRESS",null,null,"private@example.invalid",LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)));
        p=projects.member(manager,p.id(),key(),etag(p.version()),new ProjectService.Member(client.id(),"CLIENT",null,true,true));p=projects.member(manager,p.id(),key(),etag(p.version()),new ProjectService.Member(assistant.id(),"OPERATIONS",null,false,true));
        UUID photo=upload(manager,p.id(),"PHOTO"),evidence=upload(manager,p.id(),"EVIDENCE");var notice=notice(manager);
        var consent=privacy.grant(manager,key(),new PrivacyService.ConsentInput(p.id(),photo,notice.id(),evidence,"PORTFOLIO","WEBSITE",LocalDate.now().plusYears(1)));
        return new Fixture(manager,client,assistant,p.id(),photo,evidence,consent);
    }
    PrivacyService.Notice notice(Actor m){return privacy.approve(m,privacy.draft(m,key(),new PrivacyService.NoticeInput("Responsable sintético","privacy@example.invalid","Aviso sintético para consultas y portafolio web")).id(),key());}
    byte[] image(){try{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2400,1200,BufferedImage.TYPE_INT_RGB),"png",out);out.write("PRIVATE-GPS-EXIF-COMMENT".getBytes(java.nio.charset.StandardCharsets.UTF_8));return out.toByteArray();}catch(Exception e){throw new IllegalStateException(e);}}
    UUID upload(Actor owner,UUID project,String clazz){byte[] bytes=image();var d=documents.reserve(owner,key(),new DocumentService.Reservation(project,"private-photo.png","image/png",bytes.length,FileInspection.sha256(bytes),clazz));documents.upload(owner,d.document().id(),new ByteArrayInputStream(bytes),bytes.length);documents.complete(owner,d.document().id(),key());assertThat(documents.scanNext()).isTrue();return d.document().id();}
    PublicationService.Input input(Fixture f,boolean independent){return new PublicationService.Input(independent?null:f.project(),"obra-"+key(),"Proyecto autorizado","Descripción pública","Residencial","Región general",2026,List.of(new PublicationService.PhotoInput(f.photo(),f.consent().id(),"Vista del proyecto")));}
    PublicationService.Publication draft(Fixture f){return publications.create(f.assistant(),key(),input(f,false));}
    PublicationService.Publication publish(Fixture f,PublicationService.Publication p){return publications.publish(f.manager(),p.id(),key(),etag(p.version()),new PublicationService.Decision("Fotos y textos revisados",true));}
    ContactService.Input contact(Actor manager){var n=notice(manager);return new ContactService.Input("Persona sintética","visitor@example.invalid","Consulta sobre una construcción",n.id(),true,"");}
    @Test void anonymousProjectionAndDerivativeNeverExposeBusinessOrOriginals()throws Exception{
        var f=fixture();var p=draft(f);mvc.perform(get("/api/v1/public/projects/"+p.slug())).andExpect(status().isNotFound());p=publish(f,p);
        mvc.perform(get("/api/v1/publications/"+p.id()+"/assets/"+p.photos().getFirst().id()+"/preview").with(login(f.manager()))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store"));
        mvc.perform(get("/api/v1/publications/"+p.id()+"/assets/"+p.photos().getFirst().id()+"/preview").with(login(actor("ADMIN")))).andExpect(status().isNotFound());
        String body=mvc.perform(get("/api/v1/public/projects/"+p.slug())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.projectId").doesNotExist()).andExpect(jsonPath("$.photos[0].documentId").doesNotExist()).andReturn().getResponse().getContentAsString();
        for(String secret:List.of(f.project().toString(),f.photo().toString(),f.consent().id().toString(),"PRIVATE-","object_key","clientId","balance","contract"))assertThat(body).doesNotContain(secret);
        byte[] variant=mvc.perform(get("/api/v1/public/assets/"+p.photos().getFirst().id())).andExpect(status().isOk()).andExpect(content().contentType("image/jpeg")).andExpect(header().string("Cache-Control","no-store")).andReturn().getResponse().getContentAsByteArray();
        assertThat(new String(variant,java.nio.charset.StandardCharsets.ISO_8859_1)).doesNotContain("PRIVATE-GPS");var decoded=ImageIO.read(new ByteArrayInputStream(variant));assertThat(decoded.getWidth()).isEqualTo(1600);assertThat(decoded.getHeight()).isEqualTo(800);
        mvc.perform(get("/api/v1/documents/"+f.photo()+"/content")).andExpect(status().isUnauthorized());assertThat(documents.download(f.manager(),f.photo()).bytes()).containsExactly(image());
    }
    @Test void withdrawnOrExpiredConsentImmediatelyBlocksEveryControlledDistribution()throws Exception{
        var f=fixture();var p=publish(f,draft(f));UUID asset=p.photos().getFirst().id();privacy.withdraw(f.client(),f.consent().id(),key(),etag(f.consent().version()),"Retirada del titular");
        mvc.perform(get("/api/v1/public/assets/"+asset)).andExpect(status().isNotFound());mvc.perform(get("/api/v1/public/projects/"+p.slug())).andExpect(status().isNotFound());assertThat(publications.publicList(0,100).items()).extracting(PublicationService.PublicProject::slug).doesNotContain(p.slug());
        assertThat(publications.read(f.manager(),p.id()).publiclyAvailable()).isFalse();assertThat(documents.read(f.manager(),f.photo()).state()).isEqualTo("CLEAN");
        // Simular reloj con consentimiento ya vencido sin alterar las condiciones inmutables.
        var other=fixture();var n=privacy.publicNotice();UUID expired=UUID.randomUUID();jdbc.update("INSERT INTO publication_consent(id,project_id,document_id,notice_id,evidence_id,granted_by,purpose,channels,expires_on) VALUES (?,?,?,?,?,?,'PORTFOLIO','WEBSITE',current_date-1)",expired,other.project(),other.photo(),n.id(),other.evidence(),other.manager().id());
        var bad=new PublicationService.Input(other.project(),"expired-"+key(),"Vencida","Texto","Tipo","",2026,List.of(new PublicationService.PhotoInput(other.photo(),expired,"Foto")));
        assertThatThrownBy(()->publications.create(other.manager(),key(),bad)).isInstanceOf(ApiException.class);
    }
    @Test void portfolioIsIndependentAndPublicationDoesNotCloseOrCreateFinancialHistory(){
        var f=fixture();int count=jdbc.queryForObject("SELECT count(*) FROM project",Integer.class),contracts=jdbc.queryForObject("SELECT count(*) FROM contract",Integer.class);
        var p=publications.create(f.manager(),key(),input(f,true));p=publish(f,p);assertThat(p.projectId()).isNull();assertThat(jdbc.queryForObject("SELECT count(*) FROM project",Integer.class)).isEqualTo(count);assertThat(jdbc.queryForObject("SELECT count(*) FROM contract",Integer.class)).isEqualTo(contracts);assertThat(projects.read(f.manager(),f.project()).state()).isEqualTo("BORRADOR");
        jdbc.update("UPDATE project SET state='CERRADO' WHERE id=?",f.project());p=publications.withdraw(f.manager(),p.id(),key(),etag(p.version()),new PublicationService.Reason("Retirada editorial"));assertThat(p.state()).isEqualTo("WITHDRAWN");
    }
    @Test void projectScopeWrongPurposeMissingEvidenceAndStaleAuthenticationAreRejected()throws Exception{
        var f=fixture();var other=fixture();var input=input(f,false);
        assertThatThrownBy(()->publications.create(other.assistant(),key(),input)).isInstanceOf(ApiException.class);
        var mismatch=new PublicationService.Input(other.project(),input.slug(),input.title(),input.summary(),input.category(),input.region(),input.year(),input.photos());assertThatThrownBy(()->publications.create(f.manager(),key(),mismatch)).isInstanceOf(ApiException.class);
        var bad=privacy.grant(f.manager(),key(),new PrivacyService.ConsentInput(f.project(),f.photo(),privacy.publicNotice().id(),f.evidence(),"SOCIAL","INSTAGRAM",LocalDate.now().plusYears(1)));
        assertThatThrownBy(()->privacy.requireWebsiteConsent(f.manager(),bad.id(),f.photo())).isInstanceOf(ApiException.class);
        var p=draft(f);Actor stale=new Actor(f.manager().id(),f.manager().email(),f.manager().roles(),false,Instant.now().minusSeconds(301));assertThatThrownBy(()->publications.publish(stale,p.id(),key(),etag(p.version()),new PublicationService.Decision("Revisado",true))).isInstanceOf(ApiException.class);
        for(Actor a:List.of(actor("ADMIN"),f.client(),actor("PROVEEDOR"))){mvc.perform(get("/api/v1/publications/"+p.id()).with(login(a))).andExpect(status().is4xxClientError());assertThatThrownBy(()->publications.publish(a,p.id(),key(),etag(p.version()),new PublicationService.Decision("No",true))).isInstanceOf(ApiException.class);}
        jdbc.update("UPDATE document SET state='REJECTED' WHERE id=?",f.evidence());assertThatThrownBy(()->publish(f,p)).isInstanceOf(ApiException.class);
    }
    @Test void incompatiblePublishCommandsAndIdenticalRetriesHaveOneEffect()throws Exception{
        var f=fixture();var p=draft(f);String k=key();var decision=new PublicationService.Decision("Aprobación única",true);
        try(var pool=Executors.newFixedThreadPool(4)){var calls=new ArrayList<Future<PublicationService.Publication>>();for(int i=0;i<4;i++)calls.add(pool.submit(()->publications.publish(f.manager(),p.id(),k,etag(p.version()),decision)));for(var c:calls)assertThat(c.get(30,TimeUnit.SECONDS).state()).isEqualTo("PUBLISHED");}
        assertThat(jdbc.queryForObject("SELECT count(*) FROM publication_revision WHERE publication_id=? AND action='PUBLISHED'",Integer.class,p.id())).isEqualTo(1);
        assertThatThrownBy(()->publications.publish(f.manager(),p.id(),k,etag(p.version()),new PublicationService.Decision("Cambio de solicitud",true))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(409));
        assertThatThrownBy(()->publications.withdraw(f.manager(),p.id(),key(),etag(p.version()),new PublicationService.Reason("Obsoleta"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(412));
        var current=publications.read(f.manager(),p.id());assertThatThrownBy(()->publications.withdraw(f.manager(),p.id(),key(),null,new PublicationService.Reason("Sin ETag"))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(428));
        Actor assistant=f.assistant();var project=projects.read(f.manager(),f.project());projects.member(f.manager(),f.project(),key(),etag(project.version()),new ProjectService.Member(assistant.id(),"OPERATIONS",null,false,false));assertThatThrownBy(()->publications.read(assistant,current.id())).isInstanceOf(ApiException.class);
    }
    @Test void publishAuditFailureRollsBackVersionHistoryAndKey(){
        var f=fixture();var p=draft(f);jdbc.execute("CREATE FUNCTION reject_m4_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='PUBLICATION_PUBLISHED' THEN RAISE EXCEPTION 'test'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER reject_m4_audit BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION reject_m4_audit()");String k=key();
        try{assertThatThrownBy(()->publications.publish(f.manager(),p.id(),k,etag(p.version()),new PublicationService.Decision("Rollback",true))).isInstanceOf(RuntimeException.class);assertThat(publications.read(f.manager(),p.id()).state()).isEqualTo("DRAFT");assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_record WHERE key=?",Integer.class,k)).isZero();}finally{jdbc.execute("DROP TRIGGER reject_m4_audit ON audit_event");jdbc.execute("DROP FUNCTION reject_m4_audit()");}
        assertThatThrownBy(()->jdbc.update("DELETE FROM publication_revision WHERE publication_id=?",p.id())).isInstanceOf(RuntimeException.class);
    }
    @Test void siteOnlyServesApprovedContentAndEditsRequireNewApproval(){
        var m=actor("GERENTE");var c=site.read(m);c=site.edit(m,key(),etag(c.version()),new SiteContentService.Input("Titular sintético","Presentación revisada","Empresa sintética",List.of("Servicio sintético")));assertThat(site.publicRead().approved()).isFalse();assertThat(site.publicRead().heading()).isEmpty();
        c=site.approve(m,key(),etag(c.version()));assertThat(site.publicRead().approved()).isTrue();assertThatThrownBy(()->site.edit(actor("ADMIN"),key(),"\"1\"",new SiteContentService.Input("X","X","X",List.of("X")))).isInstanceOf(ApiException.class);
        site.edit(m,key(),etag(c.version()),new SiteContentService.Input("Nuevo","Nueva","Nueva",List.of("Nuevo")));assertThat(site.publicRead().approved()).isFalse();
    }
    @Test void contactIsDurableEvenWhenMailFailsAndIdempotentConcurrentRequestsPersistOnce()throws Exception{
        var m=actor("GERENTE");var input=contact(m);String k=key();List<ContactService.Receipt> receipts=new ArrayList<>();
        try(var pool=Executors.newFixedThreadPool(4)){var calls=new ArrayList<Future<ContactService.Receipt>>();for(int i=0;i<4;i++)calls.add(pool.submit(()->contacts.receive(k,input)));for(var c:calls)receipts.add(c.get());}
        assertThat(receipts.stream().map(ContactService.Receipt::reference).distinct().count()).isEqualTo(1);UUID id=receipts.getFirst().reference();assertThat(delivery.claim()).isEmpty();mail.enabled=true;mail.fail=true;var lease=delivery.claim().getFirst();final var failedLease=lease;assertThatThrownBy(()->delivery.send(failedLease)).isInstanceOf(RuntimeException.class);delivery.fail(lease);assertThat(jdbc.queryForObject("SELECT count(*) FROM contact_request WHERE id=?",Integer.class,id)).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT state FROM contact_delivery WHERE contact_id=?",String.class,id)).isEqualTo("PENDING");
        assertThatThrownBy(()->contacts.receive(k,new ContactService.Input("Otro",input.email(),input.message(),input.noticeId(),true,""))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(409));
        mail.fail=false;jdbc.update("UPDATE contact_delivery SET available_at=now() WHERE contact_id=?",id);lease=delivery.claim().getFirst();delivery.send(lease);delivery.complete(lease);assertThat(mail.sent).isEqualTo(1);assertThat(jdbc.queryForObject("SELECT state FROM contact_delivery WHERE contact_id=?",String.class,id)).isEqualTo("SENT");
    }
    @Test void expiredMailLeaseRecoversAndOldWorkerCannotConfirmOrExhaustedWorkerClaim(){
        var m=actor("GERENTE");var receipt=contacts.receive(key(),contact(m));mail.enabled=true;var old=delivery.claim().getFirst();jdbc.update("UPDATE contact_delivery SET lease_until=now()-interval '1 second' WHERE contact_id=?",receipt.reference());var current=delivery.claim().getFirst();delivery.complete(old);assertThat(jdbc.queryForObject("SELECT state FROM contact_delivery WHERE contact_id=?",String.class,receipt.reference())).isEqualTo("RUNNING");delivery.complete(current);assertThat(jdbc.queryForObject("SELECT state FROM contact_delivery WHERE contact_id=?",String.class,receipt.reference())).isEqualTo("SENT");
        var other=contacts.receive(key(),contact(m));jdbc.update("UPDATE contact_delivery SET state='RUNNING',attempts=5,lease_until=now()-interval '1 second' WHERE contact_id=?",other.reference());assertThat(delivery.claim()).isEmpty();assertThat(jdbc.queryForObject("SELECT state FROM contact_delivery WHERE contact_id=?",String.class,other.reference())).isEqualTo("DEAD");contacts.retry(m,other.reference(),key());assertThat(delivery.claim()).hasSize(1);
    }
    @Test void publicContactIsOriginBoundLimitedAndNeverReflectsInputOrEnumeratesAccounts()throws Exception{
        var m=actor("GERENTE");var input=contact(m);String k=key();String body=json.writeValueAsString(input);
        mvc.perform(post("/api/v1/public/contact").contentType("application/json").header("Origin","http://evil.example.invalid").header("Idempotency-Key",k).content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/public/contact").contentType("application/json").header("Origin","http://localhost:3000").header("Idempotency-Key",k).content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.name").doesNotExist()).andExpect(jsonPath("$.email").doesNotExist()).andExpect(jsonPath("$.status").value("RECIBIDO"));
        var malicious=new ContactService.Input("<script>alert(1)</script>",input.email(),"<script>dom input only</script>",input.noticeId(),true,"");String response=mvc.perform(post("/api/v1/public/contact").contentType("application/json").header("Origin","http://localhost:3000").header("Idempotency-Key",key()).content(json.writeValueAsString(malicious))).andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();assertThat(response).doesNotContain("script");
        mvc.perform(post("/api/v1/public/contact").contentType("application/json").header("Origin","http://localhost:3000").header("Idempotency-Key",key()).content("x".repeat(16385))).andExpect(status().isPayloadTooLarge());
        for(int i=0;i<5;i++)mvc.perform(post("/api/v1/public/contact").contentType("application/json").header("Origin","http://localhost:3000").header("Idempotency-Key",key()).content(body));
        mvc.perform(post("/api/v1/public/contact").contentType("application/json").header("Origin","http://localhost:3000").header("Idempotency-Key",key()).content(body)).andExpect(status().isTooManyRequests()).andExpect(header().string("Retry-After","600"));
        assertThat(jdbc.queryForObject("SELECT max(attempts) FROM contact_rate_limit",Integer.class)).isGreaterThan(8);
    }
    @Test void technicalAlertsContainNoContactDetailsAndUnauthorizedRolesCannotRead()throws Exception{
        var m=actor("GERENTE");var receipt=contacts.receive(key(),contact(m));jdbc.update("UPDATE contact_delivery SET state='DEAD' WHERE contact_id=?",receipt.reference());
        mvc.perform(get("/api/v1/operations/status").with(login(actor("ADMIN")))).andExpect(status().isOk()).andExpect(jsonPath("$.attentionRequired").value(true)).andExpect(jsonPath("$.failedContactNotifications").isNumber()).andExpect(jsonPath("$.contacts").doesNotExist());
        mvc.perform(get("/api/v1/operations/status").with(login(actor("CLIENTE")))).andExpect(status().isForbidden());mvc.perform(get("/actuator/health/readiness")).andExpect(status().isOk());
    }
    @Test void contactsRequireExplicitDelegationAndNoticeMustBeCurrentAndAccepted()throws Exception{
        Actor m=actor("GERENTE"),assistant=actor("ASISTENTE_ADMINISTRATIVA");var input=contact(m);contacts.receive(key(),input);
        for(Actor a:List.of(assistant,actor("ADMIN"),actor("CLIENTE")))mvc.perform(get("/api/v1/contacts").with(login(a))).andExpect(status().isForbidden());
        contacts.delegate(m,key(),new ContactService.Delegate(assistant.id(),true));assertThat(contacts.list(assistant,0,100)).isNotEmpty();contacts.delegate(m,key(),new ContactService.Delegate(assistant.id(),false));assertThatThrownBy(()->contacts.list(assistant,0,100)).isInstanceOf(ApiException.class);
        notice(m);assertThatThrownBy(()->contacts.receive(key(),input)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("NOTICE_CHANGED"));
        var current=contact(m);assertThatThrownBy(()->contacts.receive(key(),new ContactService.Input(current.name(),current.email(),current.message(),current.noticeId(),false,""))).isInstanceOf(ApiException.class);
        int count=jdbc.queryForObject("SELECT count(*) FROM contact_request",Integer.class);contacts.receive(key(),new ContactService.Input(current.name(),current.email(),current.message(),current.noticeId(),true,"spam"));assertThat(jdbc.queryForObject("SELECT count(*) FROM contact_request",Integer.class)).isEqualTo(count);
    }
}
