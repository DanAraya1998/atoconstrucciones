package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.parties.PartyService;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.*;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ato.jobs.enabled=false","ato.documents.worker-enabled=false","ato.contact.worker-enabled=false","ato.accounts.activation-worker-enabled=false","ATO_DB_PASSWORD=synthetic","ATO_OIDC_CLIENT_SECRET=synthetic","ATO_WEB_ORIGIN=http://localhost:3000","ATO_OIDC_ISSUER_URI=http://idp.example.invalid/realms/ato"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import({M0IntegrationTests.OidcTestConfiguration.class,AccountOnboardingIntegrationTests.Adapters.class})
class AccountOnboardingIntegrationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->M0IntegrationTests.PG.getJdbcUrl("postgres","postgres"));p.add("spring.datasource.username",()->"postgres");}
    static class Provisioning implements AccountProvisioningPort {
        final Map<String,Provisioned> created=new ConcurrentHashMap<>();boolean fail,mailFails;
        public boolean configured(){return true;}
        public Provisioned create(String ref,String fingerprint,String name,String email){if(fail)throw new ApiException(502,"IDENTITY_PROVIDER_UNAVAILABLE","Servicio no disponible.");return created.computeIfAbsent(ref,k->new Provisioned(M0IntegrationTests.ISSUER,UUID.randomUUID().toString()));}
        public void sendActivation(String issuer,String subject){if(mailFails)throw new IllegalStateException("Synthetic transport failure");}
    }
    @TestConfiguration static class Adapters {@Bean @Primary Provisioning provisioning(){return new Provisioning();}}
    @Autowired MockMvc mvc;@Autowired JdbcTemplate jdbc;@Autowired IdentityService identity;@Autowired AccountOnboardingService accounts;
    @Autowired AccountActivationService activation;@Autowired Provisioning provisioning;@Autowired PartyService parties;@Autowired ProjectService projects;
    @Autowired InvitationService invitations;
    @Autowired tools.jackson.databind.json.JsonMapper json;
    String key(){return UUID.randomUUID().toString();}String etag(long v){return "\""+v+"\"";}
    Actor actor(String...roles){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO user_account(id,issuer,subject,email) VALUES (?,?,?,?)",id,M0IntegrationTests.ISSUER,id.toString(),id+"@example.invalid");for(String role:roles)jdbc.update("INSERT INTO user_role VALUES (?,?)",id,role);return new Actor(id,id+"@example.invalid",Set.of(roles),false,Instant.now());}
    Actor pending(){UUID subject=UUID.randomUUID(),id=identity.register(M0IntegrationTests.ISSUER,subject.toString(),subject+"@example.invalid",true,"Persona demo");return new Actor(id,subject+"@example.invalid",Set.of(),false,Instant.now());}
    RequestPostProcessor login(Actor a){String subject=jdbc.queryForObject("SELECT subject FROM user_account WHERE id=?",String.class,a.id());return oidcLogin().idToken(t->t.subject(subject).issuer(M0IntegrationTests.ISSUER).claim("email",a.email()).claim("email_verified",true).claim("amr",List.of("pwd")).claim("auth_time",Instant.now()));}
    AccountOnboardingService.Access assistant(){return new AccountOnboardingService.Access("ASISTENTE_ADMINISTRATIVA",null,null,List.of());}
    @BeforeEach void isolate(){provisioning.fail=false;provisioning.mailFails=false;jdbc.update("UPDATE account_activation SET state='SENT' WHERE state<>'SENT'");}
    @Test void selfRegistrationWaitsForApprovalAndNeverExposesOtherIdentities()throws Exception{
        Actor a=pending(),other=pending();
        mvc.perform(get("/api/v1/accounts/me").with(login(a))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.state").value("PENDING")).andExpect(jsonPath("$.id").value(a.id().toString()));
        jdbc.update("INSERT INTO user_role VALUES (?,'CLIENTE')",a.id()); // Roles por sí solos no eluden la revisión.
        mvc.perform(get("/api/v1/session").with(login(a))).andExpect(jsonPath("$.roles").isEmpty());
        mvc.perform(get("/api/v1/client-projects").with(login(a))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/accounts/"+other.id()).with(login(a))).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/accounts/management-search").with(login(a)).with(csrf()).contentType("application/json").content("{\"query\":\"\"}")).andExpect(status().isForbidden());
    }
    @Test void approvalGrantsClientPartyAndSelectedProjectsAtomicallyAndReplays()throws Exception{
        Actor m=actor("GERENTE"),a=pending();var party=parties.create(m,"CLIENT",key(),new PartyService.Input("Cliente demo","client@example.invalid","20000000","",true));
        var own=projects.create(m,key(),new ProjectService.Input("AC-"+key().substring(0,8).toUpperCase(),"Obra demo",party.id(),"Ubicación demo",null,null,"obra@example.invalid",LocalDate.now(),LocalDate.now().plusDays(30)));
        var input=new AccountOnboardingService.Access("CLIENTE",party.id(),party.version(),List.of(new AccountOnboardingService.ProjectGrant(own.id(),own.version(),true)));
        String command=key();var result=accounts.approve(m,a.id(),command,etag(1),input);assertThat(result.state()).isEqualTo("APPROVED");assertThat(accounts.approve(m,a.id(),command,etag(1),input)).isEqualTo(result);
        mvc.perform(get("/api/v1/client-projects").with(login(a))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].id").value(own.id().toString()));
        assertThat(parties.members(m,party.id(),"CLIENT")).containsExactly(new PartyService.Member(a.id(),true));
        assertThat(projects.members(m,own.id())).containsExactly(new ProjectService.Member(a.id(),"CLIENT",null,true,true));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE resource_id=? AND action='ACCOUNT_ACCESS_APPROVED'",Integer.class,a.id())).isEqualTo(1);
    }
    @Test void wrongClientAndStaleVersionsRollbackAllGrants(){
        Actor m=actor("GERENTE"),a=pending();var party=parties.create(m,"CLIENT",key(),new PartyService.Input("Cliente propio","client@example.invalid","20000000","",true));var other=parties.create(m,"CLIENT",key(),new PartyService.Input("Otro cliente","other@example.invalid","20000000","",true));
        var project=projects.create(m,key(),new ProjectService.Input("AC-"+key().substring(0,8).toUpperCase(),"Obra ajena",other.id(),"Lugar",null,null,"obra@example.invalid",LocalDate.now(),LocalDate.now().plusDays(30)));
        assertThatThrownBy(()->accounts.approve(m,a.id(),key(),etag(1),new AccountOnboardingService.Access("CLIENTE",party.id(),party.version(),List.of(new AccountOnboardingService.ProjectGrant(project.id(),project.version(),false))))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->accounts.approve(m,a.id(),key(),etag(1),new AccountOnboardingService.Access("CLIENTE",party.id(),party.version()+1,List.of()))).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_role WHERE user_id=?",Integer.class,a.id())).isZero();assertThat(accounts.read(m,a.id()).state()).isEqualTo("PENDING");assertThat(parties.members(m,party.id(),"CLIENT")).isEmpty();
    }
    @Test void directCreationIsPreapprovedUnverifiedAndIdempotentWithoutUnsafeEmailMerge(){
        Actor m=actor("GERENTE");String email=key()+"@example.invalid",command=key();var input=new AccountOnboardingService.Direct("Persona demo",email,assistant());
        var created=accounts.create(m,command,input);assertThat(created.state()).isEqualTo("APPROVED");assertThat(created.emailVerified()).isFalse();assertThat(created.activationState()).isEqualTo("PENDING");assertThat(accounts.create(m,command,input)).isEqualTo(created);
        assertThatThrownBy(()->accounts.create(m,key(),input)).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("ACCOUNT_ALREADY_EXISTS"));
        assertThatThrownBy(()->accounts.create(m,command,new AccountOnboardingService.Direct("Otro nombre",email,assistant()))).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("IDEMPOTENCY_CONFLICT"));
        String subject=jdbc.queryForObject("SELECT subject FROM user_account WHERE id=?",String.class,created.id());
        assertThat(identity.register(M0IntegrationTests.ISSUER,subject,email,true,"Nombre verificado")).isEqualTo(created.id());assertThat(accounts.read(m,created.id()).emailVerified()).isTrue();
        var independent=identity.register("https://other.example.invalid",subject,email,true);assertThat(independent).isNotEqualTo(created.id());assertThat(accounts.read(m,independent).state()).isEqualTo("PENDING");
    }
    @Test void remoteFailureDoesNotLeaveLocalAccountsOrPermissions(){
        Actor m=actor("GERENTE");String email=key()+"@example.invalid",command=key();var input=new AccountOnboardingService.Direct("Persona demo",email,assistant());provisioning.fail=true;
        assertThatThrownBy(()->accounts.create(m,command,input)).isInstanceOf(ApiException.class);assertThat(jdbc.queryForObject("SELECT count(*) FROM user_account WHERE email=?",Integer.class,email)).isZero();
        provisioning.fail=false;assertThat(accounts.create(m,command,input).state()).isEqualTo("APPROVED");
    }
    @Test void rejectionSurvivesLoginAndSuspensionStopsExistingSessionThenResumeRestoresState()throws Exception{
        Actor m=actor("GERENTE"),a=pending();var rejected=accounts.decide(m,a.id(),"reject",key(),etag(1),new AccountOnboardingService.Decision("Solicitud no reconocida"));assertThat(rejected.state()).isEqualTo("REJECTED");
        String subject=jdbc.queryForObject("SELECT subject FROM user_account WHERE id=?",String.class,a.id());identity.register(M0IntegrationTests.ISSUER,subject,a.email(),true);
        assertThat(accounts.read(m,a.id()).state()).isEqualTo("REJECTED");
        var approved=accounts.approve(m,a.id(),key(),etag(rejected.version()),assistant());
        var suspended=accounts.decide(m,a.id(),"suspend",key(),etag(approved.version()),new AccountOnboardingService.Decision("Fin de colaboración"));
        mvc.perform(get("/api/v1/session").with(login(a))).andExpect(status().isUnauthorized());
        accounts.decide(m,a.id(),"resume",key(),etag(suspended.version()),new AccountOnboardingService.Decision("Colaboración reanudada"));mvc.perform(get("/api/v1/session").with(login(a))).andExpect(status().isOk()).andExpect(jsonPath("$.roles[0]").value("ASISTENTE_ADMINISTRATIVA"));
    }
    @Test void decisionsEnforceManagerRecentPasswordCsrfEtagAndProtectedManager()throws Exception{
        Actor m=actor("GERENTE"),a=pending();String body=json.writeValueAsString(assistant());
        for(Actor denied:List.of(a,actor("ADMIN"),actor("CLIENTE"),actor("PROVEEDOR"),actor("ASISTENTE_ADMINISTRATIVA")))mvc.perform(post("/api/v1/accounts/"+a.id()+"/approve").with(login(denied)).with(csrf()).header("Idempotency-Key",key()).header("If-Match",etag(1)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/accounts/"+a.id()+"/approve").with(login(m)).header("Idempotency-Key",key()).header("If-Match",etag(1)).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/accounts/"+a.id()+"/approve").with(login(m)).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isPreconditionRequired());
        assertThatThrownBy(()->accounts.approve(new Actor(m.id(),m.email(),m.roles(),false,Instant.now().minusSeconds(301)),a.id(),key(),etag(1),assistant())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->accounts.approve(m,m.id(),key(),etag(1),assistant())).isInstanceOf(ApiException.class);
        mvc.perform(post("/api/v1/accounts/"+a.id()+"/approve").with(login(m)).with(csrf()).header("Idempotency-Key",key()).header("If-Match",etag(1)).contentType("application/json").content(body.replace("ASISTENTE_ADMINISTRATIVA","GERENTE"))).andExpect(status().isUnprocessableContent());
    }
    @Test void concurrentApprovalsHaveOneWinnerAndOneVersionConflict()throws Exception{
        Actor m=actor("GERENTE"),a=pending();var results=new ArrayList<Future<Boolean>>();
        try(var executor=Executors.newFixedThreadPool(6)){for(int i=0;i<6;i++)results.add(executor.submit(()->{try{accounts.approve(m,a.id(),key(),etag(1),assistant());return true;}catch(ApiException e){assertThat(e.status()).isEqualTo(412);return false;}}));
            int won=0;for(var result:results)if(result.get())won++;assertThat(won).isEqualTo(1);}
        assertThat(accounts.read(m,a.id()).version()).isEqualTo(2);
    }
    @Test void activationDeliveryFailureIsDurableRetryableAndDoesNotRevokeApproval(){
        Actor m=actor("GERENTE");var account=accounts.create(m,key(),new AccountOnboardingService.Direct("Persona demo",key()+"@example.invalid",assistant()));provisioning.mailFails=true;assertThat(activation.deliverOne()).isTrue();
        var failed=accounts.read(m,account.id());assertThat(failed.activationState()).isEqualTo("FAILED");assertThat(failed.state()).isEqualTo("APPROVED");
        accounts.retryActivation(m,account.id(),key(),etag(failed.version()));provisioning.mailFails=false;assertThat(activation.deliverOne()).isTrue();assertThat(accounts.read(m,account.id()).activationState()).isEqualTo("SENT");assertThat(activation.deliverOne()).isFalse();
    }
    @Test void registrationRedirectPreservesOidcStateAndPkce()throws Exception{
        mvc.perform(get("/auth/register")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/oauth2/authorization/ato?registration=true"));
        String redirect=mvc.perform(get("/oauth2/authorization/ato?registration=true")).andExpect(status().is3xxRedirection()).andReturn().getResponse().getRedirectedUrl();
        assertThat(redirect).contains("prompt=create","code_challenge=","code_challenge_method=S256","state=");assertThat(redirect).doesNotContain("kc_action=register");
    }
    @Test void failedAuditRollsBackRolePartyProjectVersionsAndApproval(){
        Actor m=actor("GERENTE"),a=pending();var party=parties.create(m,"SUPPLIER",key(),new PartyService.Input("Proveedor demo","supplier@example.invalid","20000000","",true));
        jdbc.execute("CREATE FUNCTION fail_account_approval() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='ACCOUNT_ACCESS_APPROVED' AND NEW.resource_id='"+a.id()+"'::uuid THEN RAISE EXCEPTION 'Synthetic audit failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER fail_account_approval BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION fail_account_approval()");
        try{
            assertThatThrownBy(()->accounts.approve(m,a.id(),key(),etag(1),new AccountOnboardingService.Access("PROVEEDOR",party.id(),party.version(),List.of()))).isInstanceOf(RuntimeException.class);
            assertThat(accounts.read(m,a.id()).state()).isEqualTo("PENDING");assertThat(accounts.read(m,a.id()).version()).isEqualTo(1);assertThat(parties.members(m,party.id(),"SUPPLIER")).isEmpty();assertThat(parties.read(m,party.id(),"SUPPLIER").version()).isEqualTo(party.version());
            assertThat(jdbc.queryForObject("SELECT count(*) FROM user_role WHERE user_id=?",Integer.class,a.id())).isZero();
        }finally{jdbc.execute("DROP TRIGGER fail_account_approval ON audit_event");jdbc.execute("DROP FUNCTION fail_account_approval()");}
    }
    @Test void supplierApprovalRequiresItsPartyAndNeverGrantsContractualRepresentation(){
        Actor m=actor("GERENTE"),a=pending();var supplier=parties.create(m,"SUPPLIER",key(),new PartyService.Input("Proveedor demo","supplier@example.invalid","20000000","",true));var client=parties.create(m,"CLIENT",key(),new PartyService.Input("Cliente demo","client@example.invalid","20000000","",true));
        var project=projects.create(m,key(),new ProjectService.Input("PV-"+key().substring(0,8).toUpperCase(),"Obra demo",client.id(),"Lugar",null,null,"obra@example.invalid",LocalDate.now(),LocalDate.now().plusDays(30)));
        assertThatThrownBy(()->accounts.approve(m,a.id(),key(),etag(1),new AccountOnboardingService.Access("PROVEEDOR",supplier.id(),supplier.version(),List.of(new AccountOnboardingService.ProjectGrant(project.id(),project.version(),true))))).isInstanceOf(ApiException.class);
        accounts.approve(m,a.id(),key(),etag(1),new AccountOnboardingService.Access("PROVEEDOR",supplier.id(),supplier.version(),List.of(new AccountOnboardingService.ProjectGrant(project.id(),project.version(),false))));
        assertThat(projects.members(m,project.id())).containsExactly(new ProjectService.Member(a.id(),"SUPPLIER",supplier.id(),false,true));
        assertThat(parties.members(m,supplier.id(),"SUPPLIER")).containsExactly(new PartyService.Member(a.id(),true));
    }
    @Test void verifiedProfileChangeInvalidatesReviewAndLegacyInvitationCannotBypassRejection(){
        Actor m=actor("GERENTE"),a=pending();String subject=jdbc.queryForObject("SELECT subject FROM user_account WHERE id=?",String.class,a.id());
        identity.register(M0IntegrationTests.ISSUER,subject,"changed-"+key()+"@example.invalid",true,"Otro perfil verificado");
        assertThatThrownBy(()->accounts.approve(m,a.id(),key(),etag(1),assistant())).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.status()).isEqualTo(412));
        var invitation=invitations.create(m,a.email(),"CLIENTE");
        accounts.decide(m,a.id(),"reject",key(),etag(2),new AccountOnboardingService.Decision("Solicitud no reconocida"));
        assertThatThrownBy(()->invitations.accept(a,invitation.id(),invitation.token())).isInstanceOf(ApiException.class);
    }
}
