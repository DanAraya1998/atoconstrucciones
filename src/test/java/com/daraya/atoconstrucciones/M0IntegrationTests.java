package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.foundation.FoundationService;
import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.shared.ApiException;
import com.daraya.atoconstrucciones.shared.jobs.JobService;
import com.nimbusds.jose.*;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jwt.*;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.client.registration.*;
import org.springframework.security.oauth2.core.*;
import org.springframework.security.oauth2.core.oidc.IdTokenClaimNames;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.security.*;
import java.security.interfaces.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ato.jobs.enabled=false", "ato.documents.worker-enabled=false", "ATO_DB_PASSWORD=synthetic", "ATO_OIDC_CLIENT_SECRET=synthetic",
        "ATO_WEB_ORIGIN=http://localhost:3000", "ATO_OIDC_ISSUER_URI=http://idp.example.invalid/realms/ato"})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(M0IntegrationTests.OidcTestConfiguration.class)
class M0IntegrationTests {
    static final String ISSUER="http://idp.example.invalid/realms/ato";
    static final EmbeddedPostgres PG=startPostgres();
    static final KeyPair KEYS=keys();
    static EmbeddedPostgres startPostgres() {
        try { return EmbeddedPostgres.builder().setPort(0).start(); } catch (IOException exception) { throw new IllegalStateException(exception); }
    }
    static KeyPair keys() {
        try { var generator=KeyPairGenerator.getInstance("RSA"); generator.initialize(2048); return generator.generateKeyPair(); }
        catch (GeneralSecurityException exception) { throw new IllegalStateException(exception); }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", () -> PG.getJdbcUrl("postgres","postgres"));
        properties.add("spring.datasource.username", () -> "postgres");
    }
    @TestConfiguration
    static class OidcTestConfiguration {
        @Bean ClientRegistrationRepository clients() {
            return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId("ato")
                    .clientId("ato-web").clientSecret("synthetic").authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                    .redirectUri("http://localhost:3000/login/oauth2/code/ato").scope("openid","email")
                    .authorizationUri(ISSUER+"/authorize").tokenUri(ISSUER+"/token").jwkSetUri(ISSUER+"/keys")
                    .userInfoUri(ISSUER+"/userinfo").userNameAttributeName(IdTokenClaimNames.SUB).issuerUri(ISSUER).build());
        }
        @Bean JwtDecoder decoder() {
            var decoder=NimbusJwtDecoder.withPublicKey((RSAPublicKey) KEYS.getPublic()).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(JwtValidators.createDefaultWithIssuer(ISSUER),
                    new JwtClaimValidator<List<String>>("aud", audience -> audience!=null && audience.contains("ato-api"))));
            return decoder;
        }
    }
    @Autowired MockMvc mvc;
    @Autowired JdbcTemplate jdbc;
    @Autowired JsonMapper json;
    @Autowired IdentityService identity;
    @Autowired FoundationService resources;
    @Autowired JobService jobs;
    @Autowired InvitationService invitations;
    @Autowired SupportService support;
    @BeforeEach void isolateQueuedJobs() { jdbc.update("UPDATE outbox_job SET state='DEAD' WHERE state IN ('PENDING','RUNNING')"); }

    Actor actor(String... roles) {
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO user_account(id,issuer,subject,email) VALUES (?,?,?,?)",id,ISSUER,id.toString(),id+"@example.invalid");
        for (String role:roles) jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)",id,role);
        return new Actor(id,id+"@example.invalid",Set.of(roles),false,Instant.now());
    }
    RequestPostProcessor login(Actor actor) {
        return oidcLogin().idToken(token -> token.subject(actor.id().toString()).issuer(ISSUER)
                .claim("email",actor.email()).claim("email_verified",true).claim("amr",List.of("pwd")).claim("auth_time",Instant.now()));
    }
    String bearer(Actor actor, String audience, String issuer, Instant expires) throws JOSEException {
        var claims=new JWTClaimsSet.Builder().issuer(issuer).subject(actor.id().toString()).audience(audience)
                .issueTime(Date.from(Instant.now().minusSeconds(1))).expirationTime(Date.from(expires))
                .claim("auth_time",Instant.now().getEpochSecond()).claim("amr",List.of("pwd")).build();
        var jwt=new SignedJWT(new JWSHeader(JWSAlgorithm.RS256),claims);
        jwt.sign(new RSASSASigner((RSAPrivateKey) KEYS.getPrivate())); return jwt.serialize();
    }
    @Test void stackAndMigrationsUseRealPostgres18() {
        assertThat(jdbc.queryForObject("SHOW server_version",String.class)).startsWith("18.6");
        assertThat(jdbc.queryForList("SELECT version FROM flyway_schema_history WHERE success ORDER BY installed_rank",String.class)).containsExactly("1","2","3","4","5","6","7","8","9","10","11");
        assertThat(System.getProperty("java.version")).startsWith("27");
    }
    @Test void anonymousAndCsrfAndMixedAuthenticationAreRejected() throws Exception {
        Actor manager=actor("GERENTE");
        mvc.perform(get("/api/v1/session")).andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(post("/api/v1/foundation/resources").with(login(manager)).contentType("application/json").content("{\"name\":\"Ejemplo\"}"))
                .andExpect(status().isForbidden());
        String token=bearer(manager,"ato-api",ISSUER,Instant.now().plusSeconds(60));
        mvc.perform(post("/api/v1/foundation/resources").header("Authorization","Bearer "+token).cookie(new jakarta.servlet.http.Cookie("ATOSESSION","synthetic")))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("MIXED_AUTHENTICATION"));
        mvc.perform(get("/api/v1/session/csrf")).andExpect(status().isOk()).andExpect(jsonPath("$.token").isString());
    }
    @Test void oidcAuthorizationUsesStateAndPkceAndRejectsForgedCallback() throws Exception {
        var location=mvc.perform(get("/oauth2/authorization/ato")).andExpect(status().is3xxRedirection()).andReturn().getResponse().getHeader("Location");
        assertThat(location).contains("state=","code_challenge=","code_challenge_method=S256");
        mvc.perform(get("/login/oauth2/code/ato").param("code","forged").param("state","forged")).andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("http://localhost:3000/?authError=1"));
    }
    @Test void bearerValidatesAudienceIssuerAndExpiry() throws Exception {
        Actor manager=actor("GERENTE");
        mvc.perform(get("/api/v1/session").header("Authorization","Bearer "+bearer(manager,"ato-api",ISSUER,Instant.now().plusSeconds(60)))).andExpect(status().isOk());
        for (String token:List.of(bearer(manager,"other",ISSUER,Instant.now().plusSeconds(60)),bearer(manager,"ato-api","https://other.invalid",Instant.now().plusSeconds(60)),bearer(manager,"ato-api",ISSUER,Instant.now().minusSeconds(120)))) {
            mvc.perform(get("/api/v1/session").header("Authorization","Bearer "+token)).andExpect(status().isUnauthorized());
        }
    }
    @Test void rolesAndMembershipsProtectListsIdsAndJobs() throws Exception {
        Actor manager=actor("GERENTE"); var one=resources.create(manager,"resource-one","Obra sintética A"); var two=resources.create(manager,"resource-two","Obra sintética B");
        for (String role:List.of("CLIENTE","PROVEEDOR","ASISTENTE_ADMINISTRATIVA")) {
            Actor member=actor(role); identity.setMembership(manager,one.id(),member.id(),true);
            mvc.perform(get("/api/v1/foundation/resources/"+one.id()).with(login(member))).andExpect(status().isOk());
            mvc.perform(get("/api/v1/foundation/resources/"+two.id()).with(login(member))).andExpect(status().isNotFound());
            mvc.perform(get("/api/v1/foundation/resources").with(login(member))).andExpect(jsonPath("$.items.length()").value(1));
            mvc.perform(post("/api/v1/foundation/resources").with(login(member)).with(csrf()).header("Idempotency-Key","denied-key").contentType("application/json").content("{\"name\":\"Cambio\"}")).andExpect(status().isForbidden());
            var job=resources.enqueue(manager,two.id(),"job-for-second");
            mvc.perform(get("/api/v1/jobs/"+job.id()).with(login(member))).andExpect(status().isNotFound());
            identity.setMembership(manager,one.id(),member.id(),false);
            mvc.perform(get("/api/v1/foundation/resources/"+one.id()).with(login(member))).andExpect(status().isNotFound());
        }
        mvc.perform(put("/api/v1/foundation/resources/"+UUID.randomUUID()+"/members").with(login(manager)).with(csrf())
                .contentType("application/json").content("{\"userId\":\""+manager.id()+"\",\"active\":true}"))
                .andExpect(status().isNotFound());
        Actor admin=actor("ADMIN","CLIENTE");
        mvc.perform(get("/api/v1/foundation/resources").with(login(admin))).andExpect(jsonPath("$.items.length()").value(0));
        mvc.perform(get("/api/v1/audit/events").param("resourceId",one.id().toString()).with(login(admin))).andExpect(status().isForbidden());
        mvc.perform(put("/api/v1/users/"+admin.id()+"/roles").with(login(admin)).with(csrf()).contentType("application/json").content("{\"roles\":[\"GERENTE\"]}")).andExpect(status().isForbidden());
    }
    @Test void idempotencyAndEtagHaveOneEffectAndExplicitConflicts() throws Exception {
        Actor manager=actor("GERENTE"); String input="{\"name\":\"Recurso\"}";
        var first=mvc.perform(post("/api/v1/foundation/resources").with(login(manager)).with(csrf()).header("Idempotency-Key","create-resource").contentType("application/json").content(input)).andExpect(status().isCreated()).andExpect(header().string("ETag","\"1\"")).andReturn().getResponse().getContentAsString();
        mvc.perform(post("/api/v1/foundation/resources").with(login(manager)).with(csrf()).header("Idempotency-Key","create-resource").contentType("application/json").content(input)).andExpect(content().json(first));
        var id=UUID.fromString(json.readTree(first).get("id").asString());
        mvc.perform(post("/api/v1/foundation/resources").with(login(manager)).with(csrf()).header("Idempotency-Key","create-resource").contentType("application/json").content("{\"name\":\"Otro\"}")).andExpect(status().isConflict());
        var renamed=resources.rename(manager,id,"rename-resource","\"1\"","Actualizado"); assertThat(renamed.version()).isEqualTo(2);
        assertThat(resources.rename(manager,id,"rename-resource","\"1\"","Actualizado")).isEqualTo(renamed);
        mvc.perform(patch("/api/v1/foundation/resources/"+id).with(login(manager)).with(csrf()).header("Idempotency-Key","rename-stale").header("If-Match","\"1\"").contentType("application/json").content(input)).andExpect(status().isPreconditionFailed());
        mvc.perform(patch("/api/v1/foundation/resources/"+id).with(login(manager)).with(csrf()).header("Idempotency-Key","rename-no-etag").contentType("application/json").content(input)).andExpect(status().is(428));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE resource_id=?",Integer.class,id)).isEqualTo(2);
    }
    @Test void concurrentRequestsReturnOneResource() throws Exception {
        Actor manager=actor("GERENTE");
        try (var executor=Executors.newVirtualThreadPerTaskExecutor()) {
            var futures=new ArrayList<Future<FoundationService.Resource>>();
            for(int n=0;n<8;n++) futures.add(executor.submit(() -> resources.create(manager,"concurrent-create","Concurrente")));
            var results=new HashSet<UUID>(); for(var future:futures) results.add(future.get(15,TimeUnit.SECONDS).id());
            assertThat(results).hasSize(1);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE resource_id=?",Integer.class,results.iterator().next())).isEqualTo(1);
        }
    }
    @Test void auditFailureRollsBackEffectAndIdempotencyAndAuditCannotBeEdited() {
        Actor manager=actor("GERENTE");
        // Inyectar fallo real de persistencia exclusivamente sobre el actor del ensayo.
        jdbc.execute("CREATE FUNCTION test_reject_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.actor_id='"+manager.id()+"'::uuid THEN RAISE EXCEPTION 'synthetic failure'; END IF; RETURN NEW; END; $$");
        jdbc.execute("CREATE TRIGGER test_audit_failure BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION test_reject_audit()");
        try {
            assertThatThrownBy(() -> resources.create(manager,"audit-failure","Rollback")).isInstanceOf(RuntimeException.class);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM foundation_resource WHERE created_by=?",Integer.class,manager.id())).isZero();
            assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_record WHERE actor_id=?",Integer.class,manager.id())).isZero();
        } finally { jdbc.execute("DROP TRIGGER test_audit_failure ON audit_event"); jdbc.execute("DROP FUNCTION test_reject_audit()"); }
        var resource=resources.create(manager,"audit-success","Auditoría");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM audit_event WHERE resource_id=?",resource.id())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_event SET action='EDITED' WHERE resource_id=?",resource.id())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> jdbc.execute("TRUNCATE audit_event")).isInstanceOf(RuntimeException.class);
    }
    @Test void invitationsAreVerifiedSingleUseAndPrivilegedRolesCannotEscalate() {
        Actor manager=actor("GERENTE"), client=actor(), stranger=actor();
        var invitation=invitations.create(manager,client.email(),"CLIENTE");
        assertThatThrownBy(() -> invitations.accept(stranger,invitation.id(),invitation.token())).isInstanceOf(ApiException.class);
        invitations.accept(client,invitation.id(),invitation.token());
        assertThatThrownBy(() -> invitations.accept(client,invitation.id(),invitation.token())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> invitations.create(manager,client.email(),"GERENTE")).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> identity.setRoles(manager,client.id(),Set.of("GERENTE"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> identity.disable(manager,manager.id())).isInstanceOf(ApiException.class);
        assertThatThrownBy(() -> identity.register(ISSUER,"unverified","unverified@example.invalid",false)).isInstanceOf(ApiException.class);
    }
    @Test void supportIsApprovedScopedRecentExpiringRevocableAndReadOnly() {
        Actor manager=actor("GERENTE"), admin=actor("ADMIN"); var resource=resources.create(manager,"support-resource","Soporte");
        UUID request=support.request(admin,resource.id(),"Diagnóstico sintético");
        assertThatThrownBy(() -> resources.read(admin,resource.id())).isInstanceOf(ApiException.class);
        support.approve(manager,request); assertThat(resources.read(admin,resource.id())).isEqualTo(resource);
        assertThatThrownBy(() -> resources.rename(admin,resource.id(),"support-rename","\"1\"","Cambio")).isInstanceOf(ApiException.class);
        var other=resources.create(manager,"support-other","Otro");
        assertThatThrownBy(() -> resources.read(admin,other.id())).isInstanceOf(ApiException.class);
        Actor stale=new Actor(admin.id(),admin.email(),admin.roles(),false,Instant.now().minusSeconds(301));
        assertThatThrownBy(() -> resources.read(stale,resource.id())).isInstanceOf(ApiException.class);
        jdbc.update("UPDATE support_request SET expires_at=now()-interval '1 second' WHERE id=?",request);
        assertThatThrownBy(() -> resources.read(admin,resource.id())).isInstanceOf(ApiException.class);
        UUID another=support.request(admin,resource.id(),"Segundo diagnóstico"); support.approve(manager,another); support.revoke(manager,another);
        assertThatThrownBy(() -> resources.read(admin,resource.id())).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM audit_event WHERE action='SUPPORT_RESOURCE_READ' AND resource_id=?",Integer.class,resource.id())).isEqualTo(1);
    }
    @Test void jobsRecoverExpiredLeasesAndOnlyCurrentWorkerCommitsOneEffect() {
        Actor manager=actor("GERENTE"); var resource=resources.create(manager,"job-resource","Job"); var queued=resources.enqueue(manager,resource.id(),"enqueue-job");
        assertThat(resources.enqueue(manager,resource.id(),"enqueue-job")).isEqualTo(queued);
        var old=jobs.claim().getFirst(); assertThat(jobs.claim()).isEmpty();
        jdbc.update("UPDATE outbox_job SET lease_until=now()-interval '1 second' WHERE id=?",old.id());
        var current=jobs.claim().getFirst(); jobs.complete(old);
        assertThat(jobs.read(manager,queued.id()).state()).isEqualTo("RUNNING");
        jobs.complete(current); jobs.complete(current);
        assertThat(jobs.read(manager,queued.id()).state()).isEqualTo("SUCCEEDED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM foundation_receipt WHERE job_id=?",Integer.class,queued.id())).isEqualTo(1);
    }
    @Test void jobRetriesAndAuthorizationRevocationAreVisible() {
        Actor manager=actor("GERENTE"); var resource=resources.create(manager,"retry-resource","Retry"); var queued=resources.enqueue(manager,resource.id(),"retry-job");
        jdbc.update("UPDATE outbox_job SET max_attempts=1 WHERE id=?",queued.id()); var lease=jobs.claim().getFirst();
        jdbc.update("DELETE FROM user_role WHERE user_id=?",manager.id());
        assertThatThrownBy(() -> jobs.complete(lease)).isInstanceOf(IllegalStateException.class);
        jobs.fail(lease,"AUTHORIZATION_REVOKED");
        assertThat(jobs.read(manager,queued.id()).state()).isEqualTo("DEAD");
        assertThat(jobs.read(manager,queued.id()).errorCode()).isEqualTo("AUTHORIZATION_REVOKED");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM foundation_receipt WHERE job_id=?",Integer.class,queued.id())).isZero();
    }
    @Test void accountDisableInvalidatesExistingAuthenticationAndLogoutRequiresCsrf() throws Exception {
        Actor manager=actor("GERENTE"), client=actor("CLIENTE"); identity.disable(manager,client.id());
        mvc.perform(get("/api/v1/session").with(login(client))).andExpect(status().isUnauthorized());
        mvc.perform(post("/auth/logout").with(login(manager))).andExpect(status().isForbidden());
        mvc.perform(post("/auth/logout").with(login(manager)).with(csrf())).andExpect(status().isNoContent());
    }
    @Test void executableContractAndMoneyDateSchemasMatchTheBoundary() throws Exception {
        Actor manager=actor("GERENTE");
        mvc.perform(get("/api/v1/openapi.json").with(login(manager))).andExpect(status().isOk()).andExpect(jsonPath("$.openapi").value("3.1.1"));
        var money=new com.daraya.atoconstrucciones.shared.Money("999999999999999999.12345","CRC");
        assertThat(json.readTree(json.writeValueAsString(money)).get("amount").asString()).isEqualTo(money.amount());
        assertThat(money.decimal()).isEqualByComparingTo("999999999999999999.12345");
        assertThatThrownBy(() -> new com.daraya.atoconstrucciones.shared.Money("1.000,00","CRC")).isInstanceOf(IllegalArgumentException.class);
        assertThat(json.writeValueAsString(java.time.LocalDate.of(2028,2,29))).isEqualTo("\"2028-02-29\"");
    }
    @Test void invalidDtosAndPaginationUseConsistentProblems() throws Exception {
        Actor manager=actor("GERENTE");
        mvc.perform(post("/api/v1/foundation/resources").with(login(manager)).with(csrf()).header("Idempotency-Key","invalid-name").contentType("application/json").content("{\"name\":\" \"}"))
                .andExpect(status().isUnprocessableContent()).andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
        mvc.perform(post("/api/v1/foundation/resources").with(login(manager)).with(csrf()).header("Idempotency-Key","invalid-field").contentType("application/json").content("{\"name\":\"Ejemplo\",\"roles\":[\"GERENTE\"]}"))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/v1/foundation/resources").with(login(manager)).param("size","10000")).andExpect(status().isBadRequest());
    }
}
