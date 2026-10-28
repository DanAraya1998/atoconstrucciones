package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.parties.*;
import com.daraya.atoconstrucciones.projects.ProjectService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import java.time.*;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ato.jobs.enabled=false","ato.documents.worker-enabled=false","ATO_DB_PASSWORD=synthetic","ATO_OIDC_CLIENT_SECRET=synthetic","ATO_WEB_ORIGIN=http://localhost:3000","ATO_OIDC_ISSUER_URI=http://idp.example.invalid/realms/ato"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import({M0IntegrationTests.OidcTestConfiguration.class,M1IntegrationTests.Adapters.class})
class SupplierCategoryIntegrationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->M0IntegrationTests.PG.getJdbcUrl("postgres","postgres"));p.add("spring.datasource.username",()->"postgres");}
    @Autowired JdbcTemplate jdbc;@Autowired PartyService parties;@Autowired ProjectService projects;@Autowired MockMvc mvc;
    String key(){return UUID.randomUUID().toString();}String etag(long v){return "\""+v+"\"";}
    Actor actor(String... roles){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO user_account(id,issuer,subject,email) VALUES (?,?,?,?)",id,M0IntegrationTests.ISSUER,id.toString(),id+"@example.invalid");for(String role:roles)jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)",id,role);return new Actor(id,id+"@example.invalid",Set.of(roles),false,Instant.now());}
    RequestPostProcessor login(Actor a){return oidcLogin().idToken(t->t.subject(a.id().toString()).issuer(M0IntegrationTests.ISSUER).claim("email",a.email()).claim("email_verified",true).claim("amr",List.of("pwd")).claim("auth_time",Instant.now()));}
    PartyService.Input input(SupplierCategory category){return new PartyService.Input("Proveedor sintético","supplier@example.invalid","20000000","Especialidad conservada",true,category);}
    @Test void supplierCanBeCreatedWithoutProjectOrUserAndReplayed(){
        var manager=actor("GERENTE");String key=key();var supplier=parties.create(manager,"SUPPLIER",key,input(SupplierCategory.ELECTRICIAN));
        assertThat(parties.create(manager,"SUPPLIER",key,input(SupplierCategory.ELECTRICIAN))).isEqualTo(supplier);
        assertThat(supplier.supplierCategory()).isEqualTo(SupplierCategory.ELECTRICIAN);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM party_member WHERE party_id=?",Integer.class,supplier.id())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM project_member WHERE supplier_id=?",Integer.class,supplier.id())).isZero();
        assertThat(parties.list(manager,"SUPPLIER",0,100,SupplierCategory.ELECTRICIAN).items()).extracting(PartyService.Party::id).contains(supplier.id());
    }
    @Test void categoryChangePreservesLegacyDetailsAndOmittedCategoryAndRejectsStaleWrite(){
        var manager=actor("GERENTE");var supplier=parties.create(manager,"SUPPLIER",key(),input(SupplierCategory.PLUMBER));
        var edited=parties.edit(manager,supplier.id(),"SUPPLIER",key(),etag(supplier.version()),input(SupplierCategory.GLAZIER));
        assertThat(edited.category()).isEqualTo("Especialidad conservada");
        var legacy=parties.edit(manager,supplier.id(),"SUPPLIER",key(),etag(edited.version()),input(null));
        assertThat(legacy.supplierCategory()).isEqualTo(SupplierCategory.GLAZIER);
        assertThatThrownBy(()->parties.edit(manager,supplier.id(),"SUPPLIER",key(),etag(supplier.version()),input(SupplierCategory.MACHINERY))).hasMessageContaining("cambió");
        assertThat(parties.read(manager,supplier.id(),"SUPPLIER").supplierCategory()).isEqualTo(SupplierCategory.GLAZIER);
        assertThat(parties.create(manager,"SUPPLIER",key(),input(null)).supplierCategory()).isEqualTo(SupplierCategory.OTHER);
    }
    @Test void filterCannotExposeOtherSuppliersOrGrantProjectAccess()throws Exception{
        var manager=actor("GERENTE");var owner=actor("PROVEEDOR");var stranger=actor("PROVEEDOR");
        var own=parties.create(manager,"SUPPLIER",key(),input(SupplierCategory.FINISHES));var foreign=parties.create(manager,"SUPPLIER",key(),input(SupplierCategory.FINISHES));
        parties.member(manager,own.id(),"SUPPLIER",key(),etag(own.version()),new PartyService.Member(owner.id(),true));
        var visible=parties.list(owner,"SUPPLIER",0,1,SupplierCategory.FINISHES).items();assertThat(visible).hasSize(1);assertThat(visible.getFirst().id()).isEqualTo(own.id());assertThat(visible.getFirst().supplierCategory()).isNull();
        assertThat(parties.list(stranger,"SUPPLIER",0,100,SupplierCategory.FINISHES).items()).isEmpty();
        mvc.perform(get("/api/v1/suppliers/"+foreign.id()).with(login(owner))).andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/projects").with(login(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
        mvc.perform(get("/api/v1/suppliers").param("supplierCategory","ELECTRICIAN").with(login(owner))).andExpect(status().isOk()).andExpect(jsonPath("$.items").isEmpty());
    }
    @Test void onlyManagerCreatesAndCategoriesAreValidatedAtHttpBoundary()throws Exception{
        var manager=actor("GERENTE");
        String body="""
            {"name":"Proveedor demo","email":"supplier@example.invalid","phone":"20000000","category":"","active":true,"supplierCategory":"ELECTRICIAN"}
            """;
        for(String role:List.of("ADMIN","ASISTENTE_ADMINISTRATIVA","PROVEEDOR","CLIENTE"))mvc.perform(post("/api/v1/suppliers").with(login(actor(role))).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isForbidden());
        mvc.perform(post("/api/v1/suppliers").with(login(manager)).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body.replace("ELECTRICIAN","UNKNOWN"))).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/clients").with(login(manager)).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isUnprocessableEntity());
        mvc.perform(get("/api/v1/suppliers").with(login(manager)).param("supplierCategory","UNKNOWN")).andExpect(status().isBadRequest());
        mvc.perform(post("/api/v1/suppliers").with(login(manager)).with(csrf()).header("Idempotency-Key",key()).contentType("application/json").content(body)).andExpect(status().isCreated()).andExpect(jsonPath("$.supplierCategory").value("ELECTRICIAN"));
    }
    @Test void assistantOnlyEditsSuppliersOfAssignedProjects(){
        var manager=actor("GERENTE");var assistant=actor("ASISTENTE_ADMINISTRATIVA");var supplierUser=actor("PROVEEDOR");
        var supplier=parties.create(manager,"SUPPLIER",key(),input(SupplierCategory.MASTER_BUILDER));var other=parties.create(manager,"SUPPLIER",key(),input(SupplierCategory.HARDWARE_STORE));
        supplier=parties.member(manager,supplier.id(),"SUPPLIER",key(),etag(supplier.version()),new PartyService.Member(supplierUser.id(),true));
        var client=parties.create(manager,"CLIENT",key(),new PartyService.Input("Cliente demo","client@example.invalid","20000000","",true));
        var project=projects.create(manager,key(),new ProjectService.Input("CAT-"+key().substring(0,8).toUpperCase(),"Obra demo",client.id(),"Ubicación sintética",null,null,"project@example.invalid",LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)));
        project=projects.member(manager,project.id(),key(),etag(project.version()),new ProjectService.Member(assistant.id(),"OPERATIONS",null,false,true));
        projects.member(manager,project.id(),key(),etag(project.version()),new ProjectService.Member(supplierUser.id(),"SUPPLIER",supplier.id(),false,true));
        var edited=parties.edit(assistant,supplier.id(),"SUPPLIER",key(),etag(supplier.version()),input(SupplierCategory.FINISHES));assertThat(edited.supplierCategory()).isEqualTo(SupplierCategory.FINISHES);
        assertThatThrownBy(()->parties.edit(assistant,other.id(),"SUPPLIER",key(),etag(other.version()),input(SupplierCategory.FINISHES))).hasMessage("Recurso no disponible.");
        assertThat(parties.list(assistant,"SUPPLIER",0,100,SupplierCategory.HARDWARE_STORE).items()).isEmpty();
    }
}
