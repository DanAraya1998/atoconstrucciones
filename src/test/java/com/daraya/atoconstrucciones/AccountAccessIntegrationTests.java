package com.daraya.atoconstrucciones;
import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.parties.PartyService;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ato.jobs.enabled=false","ato.documents.worker-enabled=false","ato.contact.worker-enabled=false","ATO_DB_PASSWORD=synthetic","ATO_OIDC_CLIENT_SECRET=synthetic","ATO_WEB_ORIGIN=http://localhost:3000","ATO_OIDC_ISSUER_URI=http://idp.example.invalid/realms/ato"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import(M0IntegrationTests.OidcTestConfiguration.class)
class AccountAccessIntegrationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->M0IntegrationTests.PG.getJdbcUrl("postgres","postgres"));p.add("spring.datasource.username",()->"postgres");}
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired IdentityService identity;@Autowired AccountDirectory directory;@Autowired PartyService parties;@Autowired ProjectService projects;@Autowired InvitationService invitations;
    @Autowired tools.jackson.databind.json.JsonMapper json;
    org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder searchRequest(Actor actor,String path,String query,String role){return post(path).with(login(actor)).with(csrf()).contentType("application/json").content(json.writeValueAsString(new AccountDirectory.Search(query,role,null,null)));}
    String key(){return UUID.randomUUID().toString();}String etag(long v){return "\""+v+"\"";}
    Actor actor(String name,String role){UUID id=identity.register(M0IntegrationTests.ISSUER,key(),key()+"@example.invalid",true,name);if(role!=null){jdbc.update("INSERT INTO user_role VALUES (?,?)",id,role);jdbc.update("UPDATE user_account SET access_state='APPROVED' WHERE id=?",id);}return new Actor(id,jdbc.queryForObject("SELECT email FROM user_account WHERE id=?",String.class,id),role==null?Set.of():Set.of(role),false,Instant.now());}
    RequestPostProcessor login(Actor a){String subject=jdbc.queryForObject("SELECT subject FROM user_account WHERE id=?",String.class,a.id());return oidcLogin().idToken(t->t.subject(subject).issuer(M0IntegrationTests.ISSUER).claim("email",a.email()).claim("email_verified",true).claim("amr",List.of("pwd")).claim("auth_time",Instant.now()));}
    @Test void profileUsesVerifiedClaimsPreservesIdentityAndDoesNotGrantRoles(){
        String subject=key();UUID id=identity.register(M0IntegrationTests.ISSUER,subject,"old@example.invalid",true," Perfil demo ");
        assertThat(identity.register(M0IntegrationTests.ISSUER,subject,"NEW@example.invalid",true,null)).isEqualTo(id);
        assertThat(jdbc.queryForObject("SELECT display_name FROM user_account WHERE id=?",String.class,id)).isEqualTo("Perfil demo");
        assertThat(jdbc.queryForObject("SELECT email FROM user_account WHERE id=?",String.class,id)).isEqualTo("new@example.invalid");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM user_role WHERE user_id=?",Integer.class,id)).isZero();
        assertThatThrownBy(()->identity.register(M0IntegrationTests.ISSUER,subject,"bad@example.invalid",false,"Otro nombre")).isInstanceOf(ApiException.class);
        assertThat(jdbc.queryForObject("SELECT display_name FROM user_account WHERE id=?",String.class,id)).isEqualTo("Perfil demo");
    }
    @Test void searchIsManagerOnlyBoundedLiteralAndPrivate()throws Exception{
        Actor m=actor("Gerencia demo","GERENTE");String prefix=key();Actor c=actor(prefix+" %_! Cuenta demo","CLIENTE"),s=actor(prefix+" Cuenta demo","PROVEEDOR"),disabled=actor(prefix+" Desactivada","CLIENTE");jdbc.update("UPDATE user_account SET enabled=false WHERE id=?",disabled.id());
        mvc.perform(searchRequest(m,"/api/v1/accounts/search",prefix,"CLIENTE")).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.items.length()").value(1)).andExpect(jsonPath("$.items[0].id").value(c.id().toString())).andExpect(jsonPath("$.items[0].issuer").doesNotExist()).andExpect(jsonPath("$.items[0].subject").doesNotExist());
        assertThat(directory.search(m,"%_!","CLIENTE",0,20).items()).extracting(AccountDirectory.Account::id).contains(c.id()).doesNotContain(s.id());
        assertThat(directory.search(m,"' OR 1=1 --",null,0,20).items()).isEmpty();
        assertThat(directory.search(m,c.email(),"CLIENTE",0,1).items()).hasSize(1);
        for(Actor a:List.of(c,s,actor("Asistente","ASISTENTE_ADMINISTRATIVA"),actor("Admin","ADMIN")))mvc.perform(searchRequest(a,"/api/v1/accounts/search",prefix,null)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/accounts/search").with(csrf()).contentType("application/json").content("{\"query\":\"demo\"}")).andExpect(status().isUnauthorized());
        for(String query:List.of("", "x", "x".repeat(255)))mvc.perform(searchRequest(m,"/api/v1/accounts/search",query,null)).andExpect(status().is4xxClientError());
        mvc.perform(searchRequest(m,"/api/v1/accounts/search",prefix,"ROOT")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/accounts/search").with(login(m)).with(csrf()).contentType("application/json").content(json.writeValueAsString(new AccountDirectory.Search(prefix,null,0,51)))).andExpect(status().isUnprocessableContent());
        mvc.perform(post("/api/v1/accounts/search").with(login(m)).contentType("application/json").content("{\"query\":\"demo\"}")).andExpect(status().isForbidden());
    }
    @Test void linkedAccountsRemainReadableForRevocationButDirectoryRejectsInactiveRole(){
        Actor m=actor("Gerencia","GERENTE"),c=actor("Persona cliente","CLIENTE");var party=parties.create(m,"CLIENT",key(),new PartyService.Input("Cliente demo",c.email(),"20000000","",true));party=parties.member(m,party.id(),"CLIENT",key(),etag(party.version()),new PartyService.Member(c.id(),true));
        assertThat(parties.memberAccounts(m,party.id(),"CLIENT",0,20).items().getFirst().account().displayName()).isEqualTo("Persona cliente");
        jdbc.update("UPDATE user_account SET enabled=false WHERE id=?",c.id());
        assertThat(directory.search(m,c.email(),"CLIENTE",0,20).items()).isEmpty();assertThat(parties.memberAccounts(m,party.id(),"CLIENT",0,20).items().getFirst().account().enabled()).isFalse();
        var current=parties.read(m,party.id(),"CLIENT");parties.member(m,party.id(),"CLIENT",key(),etag(current.version()),new PartyService.Member(c.id(),false));
        assertThat(parties.memberAccounts(m,party.id(),"CLIENT",0,20).items().getFirst().active()).isFalse();
    }
    @Test void projectCandidatesRequireBothPartyAndRoleAndRespectResourcePermissions()throws Exception{
        Actor m=actor("Gerencia","GERENTE");String prefix=key();Actor own=actor(prefix+" Propio","CLIENTE"),other=actor(prefix+" Ajeno","CLIENTE"),assistant=actor(prefix+" Asistente","ASISTENTE_ADMINISTRATIVA");
        var party=parties.create(m,"CLIENT",key(),new PartyService.Input("Cliente demo",own.email(),"20000000","",true));party=parties.member(m,party.id(),"CLIENT",key(),etag(party.version()),new PartyService.Member(own.id(),true));
        var p=projects.create(m,key(),new ProjectService.Input("AC-"+key().substring(0,8).toUpperCase(),"Obra demo",party.id(),"Lugar demo",null,null,"project@example.invalid",LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)));
        assertThat(projects.accountCandidates(m,p.id(),prefix,"CLIENT",null,0,20).items()).extracting(AccountDirectory.Account::id).containsExactly(own.id());
        assertThat(projects.accountCandidates(m,p.id(),prefix,"OPERATIONS",null,0,20).items()).extracting(AccountDirectory.Account::id).contains(assistant.id());
        p=projects.member(m,p.id(),key(),etag(p.version()),new ProjectService.Member(own.id(),"CLIENT",null,true,true));
        mvc.perform(get("/api/v1/projects/"+p.id()+"/member-accounts").with(login(m))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].account.displayName").value(prefix+" Propio"));
        for(Actor a:List.of(own,other,assistant,actor("Admin","ADMIN"))){mvc.perform(searchRequest(a,"/api/v1/projects/"+p.id()+"/account-candidates",prefix,null)).andExpect(status().isForbidden());mvc.perform(get("/api/v1/clients/"+party.id()+"/member-accounts").with(login(a))).andExpect(status().isForbidden());}
        mvc.perform(searchRequest(m,"/api/v1/projects/"+key()+"/account-candidates",prefix,null)).andExpect(status().isNotFound());
        jdbc.update("DELETE FROM user_role WHERE user_id=?",own.id());
        assertThat(projects.accountCandidates(m,p.id(),prefix,"CLIENT",null,0,20).items()).isEmpty();
        final var current=p;assertThatThrownBy(()->projects.member(m,current.id(),key(),etag(current.version()),new ProjectService.Member(own.id(),"CLIENT",null,true,true))).isInstanceOf(ApiException.class);
    }
    @Test void invitationStillRequiresMatchingEmailOneUseAndRecentManagerAuthentication(){
        Actor m=actor("Gerencia","GERENTE"),recipient=actor("Invitada",null),other=actor("Otra persona",null);
        var invitation=invitations.create(m,recipient.email(),"CLIENTE");
        assertThatThrownBy(()->invitations.accept(other,invitation.id(),invitation.token())).isInstanceOf(ApiException.class);
        invitations.accept(recipient,invitation.id(),invitation.token());assertThat(jdbc.queryForList("SELECT role FROM user_role WHERE user_id=?",String.class,recipient.id())).containsExactly("CLIENTE");
        assertThatThrownBy(()->invitations.accept(recipient,invitation.id(),invitation.token())).isInstanceOf(ApiException.class);
        Actor stale=new Actor(m.id(),m.email(),m.roles(),false,Instant.now().minusSeconds(301));assertThatThrownBy(()->invitations.create(stale,recipient.email(),"CLIENTE")).isInstanceOf(ApiException.class);
    }
}
