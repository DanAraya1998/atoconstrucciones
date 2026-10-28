package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.scheduling.*;
import static com.daraya.atoconstrucciones.scheduling.ScheduleModels.*;
import com.daraya.atoconstrucciones.finance.FinancePolicy;
import com.daraya.atoconstrucciones.parties.PartyService;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.contracts.ContractService;
import com.daraya.atoconstrucciones.documents.*;
import com.daraya.atoconstrucciones.documents.fiscal.*;
import com.daraya.atoconstrucciones.costs.CostService;
import com.daraya.atoconstrucciones.treasury.TreasuryService;
import com.daraya.atoconstrucciones.reporting.ReportingService;
import com.daraya.atoconstrucciones.shared.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import java.util.*;
import java.util.concurrent.*;
import java.time.*;
import java.io.*;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties={"ato.jobs.enabled=false","ato.documents.worker-enabled=false","ATO_DB_PASSWORD=synthetic","ATO_OIDC_CLIENT_SECRET=synthetic","ATO_WEB_ORIGIN=http://localhost:3000","ATO_OIDC_ISSUER_URI=http://idp.example.invalid/realms/ato"})
@AutoConfigureMockMvc @ActiveProfiles("test") @Import({M0IntegrationTests.OidcTestConfiguration.class,M1IntegrationTests.Adapters.class})
class M3IntegrationTests {
    @DynamicPropertySource static void database(DynamicPropertyRegistry p){p.add("spring.datasource.url",()->M0IntegrationTests.PG.getJdbcUrl("postgres","postgres"));p.add("spring.datasource.username",()->"postgres");}
    @Autowired JdbcTemplate jdbc;@Autowired MockMvc mvc;@Autowired PartyService parties;@Autowired ProjectService projects;
    @Autowired ContractService contracts;@Autowired DocumentService documents;@Autowired CostService costs;@Autowired TreasuryService treasury;
    @Autowired ReportingService reports;@Autowired FiscalService fiscal;
    String key(){return UUID.randomUUID().toString();}String etag(long v){return "\""+v+"\"";}Money m(String n){return new Money(n,"CRC");}
    Actor actor(String... roles){UUID id=UUID.randomUUID();jdbc.update("INSERT INTO user_account(id,issuer,subject,email) VALUES (?,?,?,?)",id,M0IntegrationTests.ISSUER,id.toString(),id+"@example.invalid");for(String role:roles)jdbc.update("INSERT INTO user_role(user_id,role)VALUES (?,?)",id,role);return new Actor(id,id+"@example.invalid",Set.of(roles),false,Instant.now());}
    RequestPostProcessor login(Actor a){return oidcLogin().idToken(t->t.subject(a.id().toString()).issuer(M0IntegrationTests.ISSUER).claim("email",a.email()).claim("email_verified",true).claim("amr",List.of("pwd")).claim("auth_time",Instant.now()));}
    record Fixture(Actor manager,Actor client,Actor assistant,UUID party,UUID project,UUID supplier,UUID account,UUID evidence){}
    Fixture fixture(){
        Actor manager=actor("GERENTE"),client=actor("CLIENTE"),assistant=actor("ASISTENTE_ADMINISTRATIVA");
        var party=parties.create(manager,"CLIENT",key(),new PartyService.Input("Cliente sintético",client.email(),"+506 2000 0000","",true));
        party=parties.member(manager,party.id(),"CLIENT",key(),etag(party.version()),new PartyService.Member(client.id(),true));
        var p=projects.create(manager,key(),new ProjectService.Input("M3-"+key().substring(0,8).toUpperCase(),"Obra sintética",party.id(),"Ubicación sintética",null,null,"obra@example.invalid",LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)));
        p=projects.member(manager,p.id(),key(),etag(p.version()),new ProjectService.Member(client.id(),"CLIENT",null,true,true));p=projects.member(manager,p.id(),key(),etag(p.version()),new ProjectService.Member(assistant.id(),"OPERATIONS",null,false,true));
        var supplier=parties.create(manager,"SUPPLIER",key(),new PartyService.Input("Proveedor sintético","supplier@example.invalid","+506 2000 0000","Materiales",true));
        var account=treasury.account(manager,key(),new TreasuryService.AccountInput("Cuenta sintética "+key(),"BANK"));
        UUID doc=upload(manager,p.id(),image(),"image/png");
        var contract=contracts.save(manager,p.id(),key(),etag(p.version()),new ContractService.Input("M2-CON-"+key(),"Contrato sintético",m("10000000.00"),m("0.00"),LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)));
        contracts.formalize(manager,p.id(),key(),etag(contract.version()),new ContractService.Evidence(doc,"Acuerdo sintético"));
        return new Fixture(manager,client,assistant,party.id(),p.id(),supplier.id(),account.id(),doc);
    }
    byte[] image(){try{var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(2,2,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();}catch(Exception e){throw new IllegalStateException(e);}}
    UUID upload(Actor owner,UUID project,byte[] bytes,String type){var d=documents.reserve(owner,key(),new DocumentService.Reservation(project,"evidencia"+(type.equals("application/xml")?".xml":".png"),type,bytes.length,FileInspection.sha256(bytes),"EVIDENCE"));documents.upload(owner,d.document().id(),new ByteArrayInputStream(bytes),bytes.length);documents.complete(owner,d.document().id(),key());assertThat(documents.scanNext()).isTrue();return d.document().id();}
    @Autowired ScheduleService schedules;
    @Autowired com.daraya.atoconstrucciones.projects.ClientPortalService portal;
    TaskInput leaf(String name,String start,String end){return new TaskInput(UUID.randomUUID(),null,"LEAF",name,LocalDate.parse(start),LocalDate.parse(end),"PLANNED",false,false,null,List.of());}
    TaskInput edit(Task t){return new TaskInput(t.id(),t.parentId(),t.kind(),t.title(),t.startDate(),t.endExclusive(),t.state(),t.administrative(),t.clientVisible(),t.extraId(),t.supplierIds());}
    Schedule plan(Fixture f,List<TaskInput> tasks,List<Dependency> deps){var s=schedules.read(f.manager(),f.project());return schedules.change(f.manager(),f.project(),key(),etag(s.revision()),new Changes(s.revision(),tasks,deps,"Plan sintético"));}
    Schedule baseline(Fixture f,Schedule s,List<Weight> weights){s=schedules.createBaseline(f.manager(),f.project(),key(),etag(s.revision()),new BaselineInput(weights,"Entregables físicos"));return schedules.approveBaseline(f.manager(),f.project(),s.baselines().getFirst().id(),key(),etag(s.revision()),new Decision("Alcance físico aprobado"));}
    Schedule verify(Fixture f,Schedule s,UUID task,String value){var r=schedules.report(f.assistant(),f.project(),task,key(),etag(s.revision()),new ReportInput(value,LocalDate.of(2026,10,7),f.evidence(),"Medición física"));return schedules.verify(f.manager(),f.project(),r.reports().getFirst().id(),key(),etag(r.revision()),new Verification(value,f.evidence(),"Medición verificada"));}
    @Test void exclusiveEndHierarchyAndMilestonesAreValidatedAndAggregated(){
        var f=fixture();TaskInput a=leaf("Estructura","2026-10-01","2026-10-05"),b=leaf("Instalaciones","2026-10-05","2026-10-06");UUID stage=UUID.randomUUID();
        a=new TaskInput(a.id(),stage,a.kind(),a.title(),a.startDate(),a.endExclusive(),a.state(),false,false,null,List.of());
        var parent=new TaskInput(stage,null,"SUMMARY","Etapa",null,null,"PLANNED",false,false,null,List.of());
        var s=plan(f,List.of(parent,a,b),List.of(new Dependency(a.id(),b.id(),"FS",0)));
        assertThat(s.tasks().stream().filter(t->t.id().equals(stage)).findFirst().get().endExclusive()).isEqualTo(LocalDate.of(2026,10,5));
        final var first=a;final var originalDependencies=s.dependencies();assertThatThrownBy(()->plan(f,List.of(parent,first,new TaskInput(b.id(),null,"LEAF",b.title(),LocalDate.of(2026,10,4),b.endExclusive(),"PLANNED",false,false,null,List.of())),originalDependencies)).isInstanceOf(ApiException.class);
        assertThat(schedules.read(f.manager(),f.project()).revision()).isEqualTo(s.revision());
        var milestone=new TaskInput(UUID.randomUUID(),null,"MILESTONE","Entrega autorizada",LocalDate.of(2026,10,7),LocalDate.of(2026,10,7),"PLANNED",false,true,null,List.of());
        s=plan(f,List.of(parent,a,b,milestone),s.dependencies());
        final var v=s;assertThatThrownBy(()->schedules.report(f.assistant(),f.project(),milestone.id(),key(),etag(v.revision()),new ReportInput("50",LocalDate.of(2026,10,7),f.evidence(),"Hito no parcial"))).isInstanceOf(ApiException.class);
        s=baseline(f,s,List.of(new Weight(a.id(),"40",""),new Weight(b.id(),"30",""),new Weight(milestone.id(),"30","")));s=verify(f,s,milestone.id(),"100");assertThat(schedules.progress(f.client(),f.project(),null).milestones()).hasSize(1);assertThat(s.tasks().stream().filter(t->t.id().equals(milestone.id())).findFirst().get().state()).isEqualTo("COMPLETED");
    }
    @Test void longCyclesSelfLinksForeignIdsInvalidIntervalsAndAtomicity(){
        var f=fixture();List<TaskInput> nodes=new ArrayList<>();List<Dependency> edges=new ArrayList<>();for(int i=0;i<100;i++)nodes.add(new TaskInput(UUID.randomUUID(),null,"MILESTONE","Hito "+i,LocalDate.of(2026,10,7),LocalDate.of(2026,10,7),"PLANNED",false,false,null,List.of()));for(int i=1;i<nodes.size();i++)edges.add(new Dependency(nodes.get(i-1).id(),nodes.get(i).id(),"FS",0));
        var s=plan(f,nodes,edges);edges.add(new Dependency(nodes.getLast().id(),nodes.getFirst().id(),"FS",0));assertThatThrownBy(()->plan(f,nodes,edges)).isInstanceOf(ApiException.class);assertThat(schedules.read(f.manager(),f.project()).dependencies()).hasSize(99);
        var other=fixture();assertThatThrownBy(()->plan(other,List.of(nodes.getFirst()),List.of())).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->plan(f,nodes,List.of(new Dependency(nodes.getFirst().id(),nodes.getFirst().id(),"FS",0)))).isInstanceOf(ApiException.class);
        var invalid=leaf("Inválida","2026-10-07","2026-10-07");assertThatThrownBy(()->plan(other,List.of(invalid),List.of())).isInstanceOf(ApiException.class);
        var parentA=new TaskInput(UUID.randomUUID(),null,"SUMMARY","A",null,null,"PLANNED",false,false,null,List.of());var parentB=new TaskInput(UUID.randomUUID(),parentA.id(),"SUMMARY","B",null,null,"PLANNED",false,false,null,List.of());parentA=new TaskInput(parentA.id(),parentB.id(),"SUMMARY","A",null,null,"PLANNED",false,false,null,List.of());final var cycle=List.of(parentA,parentB);assertThatThrownBy(()->plan(other,cycle,List.of())).isInstanceOf(ApiException.class);
        assertThat(schedules.read(f.manager(),f.project()).revision()).isEqualTo(s.revision());
    }
    @Test void concurrentGraphMutationsCannotBothSurviveAndIdempotentRetriesHaveOneEffect()throws Exception{
        var f=fixture();var a=new TaskInput(UUID.randomUUID(),null,"MILESTONE","A",LocalDate.of(2026,10,7),LocalDate.of(2026,10,7),"PLANNED",false,false,null,List.of());var b=new TaskInput(UUID.randomUUID(),null,"MILESTONE","B",a.startDate(),a.endExclusive(),"PLANNED",false,false,null,List.of());var s=plan(f,List.of(a,b),List.of());var start=new CountDownLatch(1);List<Integer> results=new ArrayList<>();
        try(var pool=Executors.newFixedThreadPool(2)){var calls=List.of(new Dependency(a.id(),b.id(),"FS",0),new Dependency(b.id(),a.id(),"FS",0)).stream().map(edge->pool.submit(()->{start.await();try{schedules.change(f.manager(),f.project(),key(),etag(s.revision()),new Changes(s.revision(),List.of(a,b),List.of(edge),"Conflicto concurrente"));return 200;}catch(ApiException e){return e.status();}})).toList();start.countDown();for(var c:calls)results.add(c.get());}
        assertThat(results).containsExactlyInAnyOrder(200,412);assertThat(schedules.read(f.manager(),f.project()).dependencies()).hasSize(1);
        var current=schedules.read(f.manager(),f.project());String k=key();var input=new Changes(current.revision(),List.of(a,b),List.of(),"Intento único");var accepted=schedules.change(f.manager(),f.project(),k,etag(current.revision()),input);assertThat(schedules.change(f.manager(),f.project(),k,etag(current.revision()),input)).isEqualTo(accepted);
        assertThatThrownBy(()->schedules.change(f.manager(),f.project(),k,etag(current.revision()),new Changes(current.revision(),List.of(a,b),List.of(),"Otro intento"))).isInstanceOf(ApiException.class).extracting(e->((ApiException)e).status()).isEqualTo(409);
    }
    @Test void simultaneousTaskIdsAcrossProjectsNeverOverwriteAnotherProjectsTask()throws Exception{
        var first=fixture();var second=fixture();UUID id=UUID.randomUUID();var start=new CountDownLatch(1);List<Integer> results=new ArrayList<>();
        try(var pool=Executors.newFixedThreadPool(2)){var calls=List.of(first,second).stream().map(f->pool.submit(()->{var task=new TaskInput(id,null,"LEAF",f.project().toString(),LocalDate.of(2026,10,1),LocalDate.of(2026,11,1),"PLANNED",false,false,null,List.of());start.await();try{schedules.change(f.assistant(),f.project(),key(),"\"1\"",new Changes(1,List.of(task),List.of(),"IDs simultáneos"));return 200;}catch(ApiException e){return e.status();}})).toList();start.countDown();for(var call:calls)results.add(call.get());}
        assertThat(results).containsExactlyInAnyOrder(200,404);assertThat(jdbc.queryForObject("SELECT count(*) FROM schedule_task WHERE id=?",Integer.class,id)).isEqualTo(1);
        var stored=jdbc.queryForMap("SELECT project_id,title FROM schedule_task WHERE id=?",id);assertThat(stored.get("title")).isEqualTo(stored.get("project_id").toString());
    }
    @Test void fiftyPercentExampleExcludesStagesAndUnverifiedReportsAndPreservesCuts(){
        var f=fixture();TaskInput a=leaf("Estructura","2026-10-01","2026-11-01"),b=leaf("Instalaciones","2026-10-01","2026-11-01"),c=leaf("Acabados","2026-10-01","2026-11-01");UUID stage=UUID.randomUUID();
        a=new TaskInput(a.id(),stage,a.kind(),a.title(),a.startDate(),a.endExclusive(),a.state(),false,false,null,List.of());
        b=new TaskInput(b.id(),stage,b.kind(),b.title(),b.startDate(),b.endExclusive(),b.state(),false,false,null,List.of());
        c=new TaskInput(c.id(),stage,c.kind(),c.title(),c.startDate(),c.endExclusive(),c.state(),false,false,null,List.of());
        var summaryTask=new TaskInput(stage,null,"SUMMARY","Etapa sin peso propio",null,null,"PLANNED",false,false,null,List.of());
        var s=plan(f,List.of(summaryTask,a,b,c),List.of());
        assertThat(schedules.progress(f.client(),f.project(),null).percent()).isNull();s=baseline(f,s,List.of(new Weight(a.id(),"50",""),new Weight(b.id(),"30",""),new Weight(c.id(),"20","")));
        var proposed=schedules.report(f.assistant(),f.project(),a.id(),key(),etag(s.revision()),new ReportInput("80",LocalDate.of(2026,10,7),f.evidence(),"Pendiente de revisión"));assertThat(schedules.progress(f.client(),f.project(),null).percent()).isEqualTo("0.00");
        s=schedules.verify(f.manager(),f.project(),proposed.reports().getFirst().id(),key(),etag(proposed.revision()),new Verification("80",f.evidence(),"Revisión física"));s=verify(f,s,b.id(),"20");s=verify(f,s,c.id(),"20");Instant cut=Instant.now();assertThat(schedules.progress(f.client(),f.project(),null).percent()).isEqualTo("50.00");assertThat(s.tasks().stream().filter(t->t.id().equals(stage)).findFirst().get().verifiedPercent()).isEqualTo("50.00");
        s=verify(f,s,a.id(),"60");assertThat(schedules.progress(f.client(),f.project(),null).percent()).isEqualTo("40.00");assertThat(schedules.progress(f.client(),f.project(),cut).percent()).isEqualTo("50.00");
        assertThatThrownBy(()->jdbc.update("DELETE FROM schedule_revision WHERE project_id=?",f.project())).isInstanceOf(RuntimeException.class);assertThatThrownBy(()->jdbc.update("DELETE FROM progress_report WHERE project_id=?",f.project())).isInstanceOf(RuntimeException.class);
    }
    @Test void incompleteWeightsAdministrativeExclusionAndScopeChangesRequireReview(){
        var f=fixture();TaskInput a=leaf("Entregable","2026-10-01","2026-11-01"),admin=new TaskInput(UUID.randomUUID(),null,"LEAF","Administración",a.startDate(),a.endExclusive(),"PLANNED",true,false,null,List.of());var s=plan(f,List.of(a,admin),List.of());
        s=schedules.createBaseline(f.manager(),f.project(),key(),etag(s.revision()),new BaselineInput(List.of(new Weight(a.id(),"100","")),"Sin cobertura administrativa"));final var incomplete=s;assertThatThrownBy(()->schedules.approveBaseline(f.manager(),f.project(),incomplete.baselines().getFirst().id(),key(),etag(incomplete.revision()),new Decision("Aprobar"))).isInstanceOf(ApiException.class);
        s=baseline(f,s,List.of(new Weight(a.id(),"100",""),new Weight(admin.id(),"0","Actividad interna revisada")));s=verify(f,s,a.id(),"60");Instant cut=Instant.now();
        var extra=contracts.request(f.client(),f.project(),key(),new ContractService.Request("Nuevo entregable"));
        var proposed=new TaskInput(UUID.randomUUID(),null,"LEAF","Extra propuesto interno",a.startDate(),a.endExclusive(),"PLANNED",false,false,extra.id(),List.of());
        s=plan(f,List.of(a,admin,proposed),List.of());assertThat(s.progressTaskIds()).containsExactlyInAnyOrder(a.id(),admin.id());
        assertThat(schedules.progress(f.client(),f.project(),null).percent()).isEqualTo("60.00");
        extra=contracts.revise(f.manager(),f.project(),extra.id(),key(),etag(extra.version()),new ContractService.Revision("Nuevo entregable",m("100.00"),m("0.00"),0));extra=contracts.accept(f.manager(),f.project(),extra.id(),key(),etag(extra.version()),new ContractService.Evidence(f.evidence(),"Aceptación documentada"));extra=contracts.approve(f.manager(),f.project(),extra.id(),key(),etag(extra.version()),new ContractService.Evidence(f.evidence(),"Formalizar"));
        assertThat(schedules.progress(f.client(),f.project(),null).percent()).isNull();assertThat(schedules.progress(f.client(),f.project(),Instant.now()).percent()).isNull();var e=new TaskInput(UUID.randomUUID(),null,"LEAF","Extra aprobado",a.startDate(),a.endExclusive(),"PLANNED",false,false,extra.id(),List.of());var tasks=new ArrayList<>(s.tasks().stream().map(t->t.id().equals(proposed.id())?new TaskInput(t.id(),t.parentId(),t.kind(),t.title(),t.startDate(),t.endExclusive(),"CANCELLED",t.administrative(),t.clientVisible(),t.extraId(),t.supplierIds()):edit(t)).toList());tasks.add(e);s=plan(f,tasks,List.of());assertThat(s.progressTaskIds()).containsExactlyInAnyOrder(a.id(),admin.id(),e.id());
        s=baseline(f,s,List.of(new Weight(a.id(),"90",""),new Weight(admin.id(),"0","Actividad interna"),new Weight(e.id(),"10","")));var view=schedules.progress(f.client(),f.project(),null);assertThat(view.percent()).isEqualTo("54.00");assertThat(view.previousPercent()).isEqualTo("60.00");assertThat(schedules.progress(f.client(),f.project(),cut).percent()).isEqualTo("60.00");
        Instant beforeSettlement=Instant.now();contracts.cancel(f.manager(),f.project(),extra.id(),key(),etag(extra.version()),new ContractService.Adjustment(m("-50.00"),m("0.00"),0,f.evidence(),"Liquidación parcial del extra"));
        assertThat(schedules.progress(f.client(),f.project(),null).percent()).isNull();assertThat(schedules.progress(f.client(),f.project(),beforeSettlement).percent()).isEqualTo("54.00");
        s=schedules.read(f.manager(),f.project());assertThat(s.progressTaskIds()).contains(e.id());s=baseline(f,s,List.of(new Weight(a.id(),"90",""),new Weight(admin.id(),"0","Actividad interna"),new Weight(e.id(),"10","")));
        var contract=contracts.read(f.manager(),f.project(),null);contracts.adjust(f.manager(),f.project(),key(),etag(contract.version()),new ContractService.Adjustment(m("0.00"),m("0.00"),1,f.evidence(),"Revisar plazo contractual"));assertThat(schedules.progress(f.client(),f.project(),null).percent()).isNull();
    }
    @Test void supplierOnlySeesOwnAssignmentsCanReportAndRevocationBlocksReplays()throws Exception{
        var f=fixture();Actor supplier=actor("PROVEEDOR"),competitor=actor("PROVEEDOR"),admin=actor("ADMIN");var part=parties.read(f.manager(),f.supplier(),"SUPPLIER");parties.member(f.manager(),part.id(),"SUPPLIER",key(),etag(part.version()),new PartyService.Member(supplier.id(),true));var p=projects.read(f.manager(),f.project());projects.member(f.manager(),p.id(),key(),etag(p.version()),new ProjectService.Member(supplier.id(),"SUPPLIER",f.supplier(),false,true));
        var secret=leaf("Nombre interno reservado","2026-10-01","2026-10-04");var own=new TaskInput(UUID.randomUUID(),null,"LEAF","Asignada",LocalDate.of(2026,10,4),LocalDate.of(2026,10,8),"PLANNED",false,false,null,List.of(f.supplier()));var s=plan(f,List.of(secret,own),List.of(new Dependency(secret.id(),own.id(),"FS",0)));
        var view=schedules.assigned(supplier,f.project());assertThat(view.items()).hasSize(1);assertThat(view.items().getFirst().earliestStart()).isEqualTo(LocalDate.of(2026,10,4));
        mvc.perform(get("/api/v1/projects/"+f.project()+"/schedule/tasks").with(login(supplier))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].supplierIds").doesNotExist()).andExpect(jsonPath("$.items[0].extraId").doesNotExist()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString(secret.id().toString()))));
        for(Actor a:List.of(f.client(),supplier))mvc.perform(get("/api/v1/projects/"+f.project()+"/schedule").with(login(a))).andExpect(status().isForbidden());
        for(Actor a:List.of(admin,competitor))mvc.perform(get("/api/v1/projects/"+f.project()+"/schedule/tasks").with(login(a))).andExpect(status().isNotFound());
        UUID doc=upload(supplier,f.project(),image(),"image/png");String k=key();var input=new ReportInput("25",LocalDate.of(2026,10,7),doc,"Reporte asignado");var r=schedules.report(supplier,f.project(),own.id(),k,etag(s.revision()),input);assertThat(r.reports()).hasSize(1);assertThat(schedules.progress(f.client(),f.project(),null).percent()).isNull();
        assertThatThrownBy(()->schedules.report(supplier,f.project(),secret.id(),key(),etag(r.revision()),input)).isInstanceOf(ApiException.class);assertThatThrownBy(()->schedules.verify(supplier,f.project(),r.reports().getFirst().id(),key(),etag(r.revision()),new Verification("25",doc,"No autorizado"))).isInstanceOf(ApiException.class);
        var project=projects.read(f.manager(),f.project());projects.member(f.manager(),project.id(),key(),etag(project.version()),new ProjectService.Member(supplier.id(),"SUPPLIER",f.supplier(),false,false));assertThatThrownBy(()->schedules.report(supplier,f.project(),own.id(),k,etag(s.revision()),input)).isInstanceOf(ApiException.class);
    }
    @Test void clientSummaryContainsNoAdministrativeTaskSupplierOrEvidenceIds()throws Exception{
        var f=fixture();var other=fixture();var secret=leaf("INTERNAL-SENTINEL","2026-10-01","2026-11-01");plan(f,List.of(secret),List.of());
        mvc.perform(get("/api/v1/projects/"+f.project()+"/progress-summary").with(login(f.client()))).andExpect(status().isOk()).andExpect(jsonPath("$.percent").isEmpty()).andExpect(jsonPath("$.tasks").doesNotExist()).andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("INTERNAL-SENTINEL"))));
        mvc.perform(get("/api/v1/projects/"+other.project()+"/progress-summary").with(login(f.client()))).andExpect(status().isNotFound());
        var party=parties.read(f.manager(),f.party(),"CLIENT");parties.member(f.manager(),party.id(),"CLIENT",key(),etag(party.version()),new PartyService.Member(f.client().id(),false));mvc.perform(get("/api/v1/projects/"+f.project()+"/progress-summary").with(login(f.client()))).andExpect(status().isNotFound());
    }
    @Test void portalUsesSafeAllowlistedDtosAndPreservesOwnCommandPermissions()throws Exception {
        var f=fixture();var other=fixture();var input=new TreasuryService.DepositInput(m("100.00"),LocalDate.of(2026,10,7),"BANK_TRANSFER","SYNTHETIC",upload(f.client(),f.project(),image(),"image/png"),"Mi comprobante");
        var deposit=portal.report(f.client(),f.project(),key(),input);assertThat(deposit.state()).isEqualTo("REPORTED");
        var extra=portal.request(f.client(),f.project(),key(),new ContractService.Request("Extra de portal"));assertThat(extra.state()).isEqualTo("SOLICITADO");
        var view=portal.read(f.client(),f.project(),0);assertThat(view.representative()).isTrue();assertThat(view.deposits()).hasSize(1);assertThat(view.finance().collectionDue().amount()).isEqualTo("10000000.00");
        assertThat(portal.list(f.client(),0,100).items()).extracting(com.daraya.atoconstrucciones.projects.ClientPortalService.ProjectCard::id).containsExactly(f.project());
        mvc.perform(get("/api/v1/projects/"+f.project()+"/client-portal").with(login(f.client()))).andExpect(status().isOk()).andExpect(jsonPath("$.project.clientId").doesNotExist()).andExpect(jsonPath("$.deposits[0].movementId").doesNotExist()).andExpect(jsonPath("$.contract.amendments").doesNotExist()).andExpect(jsonPath("$.finance.validatedCosts").doesNotExist()).andExpect(jsonPath("$.extras[0].revisions[0].acceptedBy").doesNotExist());
        mvc.perform(get("/api/v1/client-projects").with(login(f.client()))).andExpect(status().isOk()).andExpect(jsonPath("$.items[0].clientId").doesNotExist());
        mvc.perform(get("/api/v1/projects/"+other.project()+"/client-portal").with(login(f.client()))).andExpect(status().isNotFound());
        assertThatThrownBy(()->portal.report(f.client(),other.project(),key(),input)).isInstanceOf(ApiException.class);assertThatThrownBy(()->portal.read(f.assistant(),f.project(),0)).isInstanceOf(ApiException.class);
    }
    @Test void retiredTasksAreRetainedAndUnapprovedMilestoneTitlesStayPrivate(){
        var f=fixture();var milestone=new TaskInput(UUID.randomUUID(),null,"MILESTONE","PRIVATE-UNTIL-APPROVED",LocalDate.of(2026,10,7),LocalDate.of(2026,10,7),"PLANNED",false,true,null,List.of());var s=plan(f,List.of(milestone),List.of());assertThat(schedules.progress(f.client(),f.project(),null).milestones()).isEmpty();
        s=baseline(f,s,List.of(new Weight(milestone.id(),"100","")));assertThat(schedules.progress(f.client(),f.project(),null).milestones()).hasSize(1);s=verify(f,s,milestone.id(),"100");
        final var current=s;assertThatThrownBy(()->schedules.change(f.assistant(),f.project(),key(),etag(current.revision()),new Changes(current.revision(),List.of(new TaskInput(milestone.id(),null,"MILESTONE",milestone.title(),milestone.startDate(),milestone.endExclusive(),"CANCELLED",false,true,null,List.of())),List.of(),"Retirar"))).isInstanceOf(ApiException.class);
        s=plan(f,List.of(new TaskInput(milestone.id(),null,"MILESTONE",milestone.title(),milestone.startDate(),milestone.endExclusive(),"CANCELLED",false,true,null,List.of())),List.of());assertThat(s.tasks()).hasSize(1);assertThat(schedules.progress(f.client(),f.project(),null).percent()).isNull();
        assertThatThrownBy(()->jdbc.update("DELETE FROM schedule_task WHERE id=?",milestone.id())).isInstanceOf(RuntimeException.class);assertThatThrownBy(()->plan(f,List.of(),List.of())).isInstanceOf(ApiException.class);
    }
    @Test void cleanEvidencePasswordClosedGuardAndAuditFailureAreEnforced(){
        var f=fixture();TaskInput a=leaf("Entregable","2026-10-01","2026-11-01");var s=plan(f,List.of(a),List.of());
        Actor stale=new Actor(f.manager().id(),f.manager().email(),f.manager().roles(),false,Instant.now().minusSeconds(301));assertThatThrownBy(()->schedules.createBaseline(stale,f.project(),key(),etag(s.revision()),new BaselineInput(List.of(new Weight(a.id(),"100","")),"Aprobar"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->schedules.change(f.manager(),f.project(),key(),null,new Changes(s.revision(),List.of(a),List.of(),"Editar"))).isInstanceOf(ApiException.class).extracting(e->((ApiException)e).status()).isEqualTo(428);
        var doc=documents.reserve(f.assistant(),key(),new DocumentService.Reservation(f.project(),"pendiente.png","image/png",image().length,FileInspection.sha256(image()),"EVIDENCE"));assertThatThrownBy(()->schedules.report(f.assistant(),f.project(),a.id(),key(),etag(s.revision()),new ReportInput("10",LocalDate.of(2026,10,7),doc.document().id(),"Pendiente"))).isInstanceOf(ApiException.class);
        jdbc.execute("CREATE FUNCTION reject_m3_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='SCHEDULE_CHANGED' THEN RAISE EXCEPTION 'test'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER reject_m3_audit BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION reject_m3_audit()");String k=key();try{assertThatThrownBy(()->schedules.change(f.manager(),f.project(),k,etag(s.revision()),new Changes(s.revision(),List.of(a),List.of(),"Rollback"))).isInstanceOf(RuntimeException.class);assertThat(schedules.read(f.manager(),f.project()).revision()).isEqualTo(s.revision());assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_record WHERE key=?",Integer.class,k)).isZero();}finally{jdbc.execute("DROP TRIGGER reject_m3_audit ON audit_event");jdbc.execute("DROP FUNCTION reject_m3_audit()");}
        jdbc.update("UPDATE project SET state='CERRADO' WHERE id=?",f.project());assertThatThrownBy(()->schedules.change(f.manager(),f.project(),key(),etag(s.revision()),new Changes(s.revision(),List.of(a),List.of(),"Editar cerrado"))).isInstanceOf(ApiException.class);
    }
}
