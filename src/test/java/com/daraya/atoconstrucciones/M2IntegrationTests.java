package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.identity.*;
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
class M2IntegrationTests {
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
        var p=projects.create(manager,key(),new ProjectService.Input("M2-"+key().substring(0,8).toUpperCase(),"Obra sintética",party.id(),"Ubicación sintética",null,null,"obra@example.invalid",LocalDate.of(2026,10,1),LocalDate.of(2026,12,1)));
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
    CostService.Input expenseInput(Fixture f,String net,String tax,List<CostService.AllocationInput> allocations,UUID voucher,UUID original){return new CostService.Input(f.supplier(),"",LocalDate.of(2026,10,7),"MATERIALS","Materiales sintéticos",m(net),m(tax),voucher,null,original,"Porción revisada",f.evidence(),allocations);}
    CostService.Expense expense(Fixture f,String amount){return costs.create(f.manager(),key(),expenseInput(f,amount,"0.00",List.of(new CostService.AllocationInput(f.project(),null,m(amount),m("0.00"))),null,null));}
    CostService.Expense validate(Fixture f,CostService.Expense e){return costs.decide(f.manager(),e.id(),key(),etag(e.version()),"validate",new CostService.Decision(f.evidence(),"Emisor, receptor y porción revisados"));}
    TreasuryService.Movement movement(Fixture f,String kind,String amount,UUID source){return treasury.create(f.manager(),key(),new TreasuryService.MovementInput(f.project(),f.account(),kind,Set.of("PAYMENT","ADVANCE","SUPPLIER_REFUND").contains(kind)?f.supplier():null,m(amount),LocalDate.of(2026,10,7),"BANK_TRANSFER","REFERENCIA",key(),source,f.evidence(),""));}
    TreasuryService.Movement confirm(Fixture f,TreasuryService.Movement movement){return treasury.confirm(f.manager(),movement.id(),key(),etag(movement.version()),new TreasuryService.Decision(f.evidence(),"Movimiento real verificado"));}
    TreasuryService.Deposit deposit(Fixture f,String amount){return treasury.report(f.client(),f.project(),key(),new TreasuryService.DepositInput(m(amount),LocalDate.of(2026,10,7),"BANK_TRANSFER","REF-COINCIDENTE",upload(f.client(),f.project(),image(),"image/png"),"Pago reportado"));}
    ReportingService.Summary summary(Fixture f){return reports.project(f.manager(),f.project(),null);}
    @Test void pendingReportDoesNotReduceDebtAndConfirmationDuplicateCreditAndRefundAreExplained(){
        var f=fixture();UUID clientEvidence=upload(f.client(),f.project(),image(),"image/png");var d=treasury.report(f.client(),f.project(),key(),new TreasuryService.DepositInput(m("11000000.00"),LocalDate.of(2026,10,7),"BANK_TRANSFER","REF",clientEvidence,"Pago"));
        assertThat(summary(f).collectionDue().amount()).isEqualTo("10000000.00");assertThat(summary(f).reportedDeposits().amount()).isEqualTo("11000000.00");
        d=treasury.confirmDeposit(f.manager(),f.project(),d.id(),key(),etag(d.version()),new TreasuryService.ConfirmDeposit(null,f.account(),key(),f.evidence(),m("11000000.00"),"Confirmación bancaria"));
        assertThat(summary(f).customerCredit().amount()).isEqualTo("1000000.00");assertThat(summary(f).confirmedCashReceipts().amount()).isEqualTo("11000000.00");
        var duplicate=deposit(f,"11000000.00");duplicate=treasury.confirmDeposit(f.manager(),f.project(),duplicate.id(),key(),etag(duplicate.version()),new TreasuryService.ConfirmDeposit(d.movementId(),null,null,null,m("0.00"),"Mismo hecho"));assertThat(duplicate.state()).isEqualTo("DUPLICATE");assertThat(summary(f).confirmedCashReceipts().amount()).isEqualTo("11000000.00");
        confirm(f,movement(f,"CUSTOMER_REFUND","1000000.00",d.movementId()));assertThat(summary(f).customerCredit().amount()).isEqualTo("0.00");assertThat(summary(f).appliedCollections().amount()).isEqualTo("10000000.00");
    }
    @Test void referenceCoincidenceAlertsWithoutRejectingAndFutureDateAndCurrencyAreRejected(){
        var f=fixture();var a=deposit(f,"10.00");var b=deposit(f,"20.00");assertThat(b.referenceCoincidence()).isTrue();assertThat(treasury.readDeposit(f.client(),f.project(),a.id()).referenceCoincidence()).isTrue();
        assertThatThrownBy(()->treasury.report(f.client(),f.project(),key(),new TreasuryService.DepositInput(m("10.00"),LocalDate.now(FinancePolicy.ZONE).plusDays(1),"BANK_TRANSFER","",f.evidence(),""))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->treasury.report(f.client(),f.project(),key(),new TreasuryService.DepositInput(new Money("10.00","USD"),LocalDate.now(),"BANK_TRANSFER","",f.evidence(),""))).isInstanceOf(ApiException.class);
    }
    @Test void recognizedCostIsSeparateFromPaymentsAndPartialAdvanceIsNotPaidTwice(){
        var f=fixture();var advance=confirm(f,movement(f,"ADVANCE","400000.00",null));assertThat(summary(f).validatedCosts().amount()).isEqualTo("0.00");assertThat(summary(f).unappliedAdvances().amount()).isEqualTo("400000.00");
        var e=expense(f,"600000.00");assertThat(summary(f).registeredCosts().amount()).isEqualTo("600000.00");assertThat(summary(f).validatedCosts().amount()).isEqualTo("0.00");e=validate(f,e);
        treasury.apply(f.manager(),advance.id(),key(),etag(advance.version()),new TreasuryService.Application(e.id(),m("400000.00"),"Anticipo aplicado"));assertThat(summary(f).cashPayments().amount()).isEqualTo("400000.00");assertThat(summary(f).validatedDebtPending().amount()).isEqualTo("200000.00");assertThat(summary(f).unappliedAdvances().amount()).isEqualTo("0.00");
        var payment=confirm(f,movement(f,"PAYMENT","200000.00",null));treasury.apply(f.manager(),payment.id(),key(),etag(payment.version()),new TreasuryService.Application(e.id(),m("200000.00"),"Saldo pagado"));assertThat(summary(f).cashPayments().amount()).isEqualTo("600000.00");assertThat(summary(f).validatedDebtPending().amount()).isEqualTo("0.00");
    }
    @Test void sharedInvoiceCountsOnlyItsAllocationsAndRejectsOverAllocationOrForeignExtra(){
        var f=fixture();var other=fixture();var e=costs.create(f.manager(),key(),expenseInput(f,"200000.00","26000.00",List.of(new CostService.AllocationInput(f.project(),null,m("100000.00"),m("13000.00")),new CostService.AllocationInput(other.project(),null,m("100000.00"),m("13000.00"))),null,null));validate(f,e);
        assertThat(summary(f).validatedCosts().amount()).isEqualTo("113000.00");assertThat(summary(other).validatedCosts().amount()).isEqualTo("113000.00");
        assertThatThrownBy(()->costs.read(f.assistant(),e.id())).isInstanceOf(ApiException.class);assertThat(costs.list(f.assistant(),f.project(),0,30).getFirst().allocatedTotal().amount()).isEqualTo("113000.00");
        assertThatThrownBy(()->costs.create(f.manager(),key(),expenseInput(f,"10.00","0.00",List.of(new CostService.AllocationInput(f.project(),null,m("11.00"),m("0.00"))),null,null))).isInstanceOf(ApiException.class);
        var extra=contracts.request(other.client(),other.project(),key(),new ContractService.Request("Extra de otra obra"));assertThatThrownBy(()->costs.create(f.manager(),key(),expenseInput(f,"10.00","0.00",List.of(new CostService.AllocationInput(f.project(),extra.id(),m("10.00"),m("0.00"))),null,null))).isInstanceOf(ApiException.class);
    }
    @Test void concurrencyProducesOneConfirmationAndStableIdempotentResult()throws Exception{
        var f=fixture();var d=deposit(f,"4000000.00");String k=key();var input=new TreasuryService.ConfirmDeposit(null,f.account(),key(),f.evidence(),m("4000000.00"),"Verificación bancaria");var start=new CountDownLatch(1);
        try(var executor=Executors.newFixedThreadPool(8)){List<Future<TreasuryService.Deposit>> calls=new ArrayList<>();for(int i=0;i<8;i++)calls.add(executor.submit(()->{start.await();return treasury.confirmDeposit(f.manager(),f.project(),d.id(),k,etag(d.version()),input);}));start.countDown();UUID first=calls.getFirst().get().movementId();for(var call:calls)assertThat(call.get().movementId()).isEqualTo(first);}
        assertThat(summary(f).confirmedCashReceipts().amount()).isEqualTo("4000000.00");assertThat(jdbc.queryForObject("SELECT count(*) FROM treasury_movement WHERE project_id=?",Integer.class,f.project())).isEqualTo(1);
        assertThatThrownBy(()->treasury.confirmDeposit(f.manager(),f.project(),d.id(),k,etag(d.version()),new TreasuryService.ConfirmDeposit(null,f.account(),key(),f.evidence(),m("1.00"),"Otro body"))).isInstanceOf(ApiException.class).extracting(x->((ApiException)x).status()).isEqualTo(409);
    }
    @Test void independentCashCommandsCannotDuplicateOneFactAndObligationLimitsHoldConcurrently()throws Exception{
        var f=fixture();var e=validate(f,expense(f,"100.00"));String fact=key();var i=new TreasuryService.MovementInput(f.project(),f.account(),"PAYMENT",f.supplier(),m("100.00"),LocalDate.of(2026,10,7),"BANK_TRANSFER","",fact,null,f.evidence(),"");var first=confirm(f,treasury.create(f.manager(),key(),i));var duplicate=treasury.create(f.manager(),key(),i);assertThatThrownBy(()->confirm(f,duplicate)).isInstanceOf(ApiException.class);
        var second=confirm(f,movement(f,"PAYMENT","100.00",null));var latch=new CountDownLatch(1);List<Boolean> results=new ArrayList<>();
        try(var pool=Executors.newFixedThreadPool(2)){var calls=List.of(first,second).stream().map(mv->pool.submit(()->{latch.await();try{treasury.apply(f.manager(),mv.id(),key(),etag(mv.version()),new TreasuryService.Application(e.id(),m("100.00"),"Aplicar"));return true;}catch(ApiException ex){return false;}})).toList();latch.countDown();for(var call:calls)results.add(call.get());}
        assertThat(results).containsExactlyInAnyOrder(true,false);assertThat(summary(f).appliedPayments().amount()).isEqualTo("100.00");
    }
    @Test void partialReversalUnwindsApplicationsAndKeepsOldCutUnchanged(){
        var f=fixture();var d=deposit(f,"500.00");d=treasury.confirmDeposit(f.manager(),f.project(),d.id(),key(),etag(d.version()),new TreasuryService.ConfirmDeposit(null,f.account(),key(),f.evidence(),m("400.00"),"Confirmar"));Instant cut=Instant.now();UUID movementId=d.movementId();var movement=treasury.read(f.manager(),movementId);
        movement=treasury.reverse(f.manager(),movement.id(),key(),etag(movement.version()),new TreasuryService.Reverse(m("200.00"),"Corrección del hecho"));assertThat(summary(f).confirmedCashReceipts().amount()).isEqualTo("300.00");assertThat(summary(f).appliedCollections().amount()).isEqualTo("300.00");assertThat(reports.project(f.manager(),f.project(),cut).confirmedCashReceipts().amount()).isEqualTo("500.00");
        assertThatThrownBy(()->treasury.reverse(f.manager(),movementId,key(),"\"1\"",new TreasuryService.Reverse(m("10.00"),"Conflicto"))).isInstanceOf(ApiException.class);
        final var remaining=movement;assertThatThrownBy(()->treasury.reverse(f.manager(),remaining.id(),key(),etag(remaining.version()),new TreasuryService.Reverse(m("301.00"),"Excede"))).isInstanceOf(ApiException.class);
    }
    @Test void historicalPendingReportAndExpenseRemainPendingAtTheirOriginalCut(){
        var f=fixture();var d=deposit(f,"500.00");var e=expense(f,"100.00");Instant cut=Instant.now();validate(f,e);treasury.reviewDeposit(f.manager(),f.project(),d.id(),key(),etag(d.version()),"reject",new TreasuryService.Decision(null,"Reporte rechazado"));
        var old=reports.project(f.manager(),f.project(),cut);assertThat(old.reportedDeposits().amount()).isEqualTo("500.00");assertThat(old.registeredCosts().amount()).isEqualTo("100.00");assertThat(old.validatedCosts().amount()).isEqualTo("0.00");assertThat(summary(f).reportedDeposits().amount()).isEqualTo("0.00");
    }
    @Test void fiscalImportsAreProposalsAndSameKeyCannotCreateAnotherCost(){
        var f=fixture();String xml=fiscalXml("FacturaElectronica",null,"1130000.00000");UUID doc=upload(f.manager(),f.project(),xml.getBytes(),"application/xml");var v=fiscal.importXml(f.manager(),key(),new FiscalService.Import(f.project(),doc));var again=fiscal.importXml(f.manager(),key(),new FiscalService.Import(f.project(),doc));assertThat(again.id()).isEqualTo(v.id());assertThat(summary(f).validatedCosts().amount()).isEqualTo("0.00");assertThat(summary(f).cashPayments().amount()).isEqualTo("0.00");
        var e=costs.create(f.manager(),key(),expenseInput(f,"1000000.00000","130000.00000",List.of(new CostService.AllocationInput(f.project(),null,m("1000000.00000"),m("130000.00000"))),v.id(),null));validate(f,e);
        assertThatThrownBy(()->costs.create(f.manager(),key(),expenseInput(f,"1000000.00000","130000.00000",List.of(new CostService.AllocationInput(f.project(),null,m("1000000.00000"),m("130000.00000"))),v.id(),null))).isInstanceOf(ApiException.class);
        UUID altered=upload(f.manager(),f.project(),xml.replace("Material sintetico","Material distinto").getBytes(),"application/xml");assertThatThrownBy(()->fiscal.importXml(f.manager(),key(),new FiscalService.Import(f.project(),altered))).isInstanceOf(ApiException.class).extracting(x->((ApiException)x).code()).isEqualTo("DIVERGENT_FISCAL_KEY");
    }
    String fiscalXml(String type,String reference,String total){String xml=FiscalParserTests.fixture(type);String fiscalKey="506071026"+String.format("%041d",new BigDecimal(new java.math.BigInteger(key().replace("-",""),16)).toBigInteger());xml=xml.replace("50607102600310100000000100001010000000001100000001",fiscalKey);if(reference!=null)xml=xml.replace("50607102600310100000000100001010000000001100000002",reference);if(total.equals("113000.00000"))xml=xml.replace("1000000.00000","100000.00000").replace("130000.00000","13000.00000").replace("1130000.00000",total);return xml;}
    @Test void creditReducesCostOnceAndRefundChangesCashSeparatelyWhileDebitIncreasesCost(){
        var f=fixture();String original=fiscalXml("FacturaElectronica",null,"1130000.00000");UUID doc=upload(f.manager(),f.project(),original.getBytes(),"application/xml");var v=fiscal.importXml(f.manager(),key(),new FiscalService.Import(f.project(),doc));var e=costs.create(f.manager(),key(),expenseInput(f,"1000000.00000","130000.00000",List.of(new CostService.AllocationInput(f.project(),null,m("1000000.00000"),m("130000.00000"))),v.id(),null));e=validate(f,e);
        var payment=confirm(f,movement(f,"PAYMENT","1130000.00",null));payment=treasury.apply(f.manager(),payment.id(),key(),etag(payment.version()),new TreasuryService.Application(e.id(),m("1130000.00"),"Pago completo"));
        String credit=fiscalXml("NotaCreditoElectronica",v.extraction().key(),"113000.00000");var nc=fiscal.importXml(f.manager(),key(),new FiscalService.Import(f.project(),upload(f.manager(),f.project(),credit.getBytes(),"application/xml")));var creditExpense=costs.create(f.manager(),key(),expenseInput(f,"100000.00000","13000.00000",List.of(new CostService.AllocationInput(f.project(),null,m("100000.00000"),m("13000.00000"))),nc.id(),e.id()));validate(f,creditExpense);
        assertThat(summary(f).validatedCosts().amount()).isEqualTo("1017000.00");assertThat(summary(f).cashPayments().amount()).isEqualTo("1130000.00");assertThat(summary(f).supplierCredit().amount()).isEqualTo("113000.00");confirm(f,movement(f,"SUPPLIER_REFUND","113000.00",payment.id()));assertThat(summary(f).cashPayments().amount()).isEqualTo("1017000.00");assertThat(summary(f).supplierCredit().amount()).isEqualTo("0.00");
        String debit=fiscalXml("NotaDebitoElectronica",v.extraction().key(),"113000.00000");var nd=fiscal.importXml(f.manager(),key(),new FiscalService.Import(f.project(),upload(f.manager(),f.project(),debit.getBytes(),"application/xml")));var debitExpense=costs.create(f.manager(),key(),expenseInput(f,"100000.00000","13000.00000",List.of(new CostService.AllocationInput(f.project(),null,m("100000.00000"),m("13000.00000"))),nd.id(),e.id()));validate(f,debitExpense);assertThat(summary(f).validatedCosts().amount()).isEqualTo("1130000.00");assertThat(summary(f).validatedDebtPending().amount()).isEqualTo("113000.00");
    }
    @Test void reversalDoesNotErasePaidCostAndDatabaseProtectsFinancialHistory(){
        var f=fixture();var e=validate(f,expense(f,"100.00"));var p=confirm(f,movement(f,"PAYMENT","100.00",null));treasury.apply(f.manager(),p.id(),key(),etag(p.version()),new TreasuryService.Application(e.id(),m("100.00"),"Pagar"));costs.decide(f.manager(),e.id(),key(),etag(e.version()),"reverse",new CostService.Decision(null,"Costo equivocado"));assertThat(summary(f).validatedCosts().amount()).isEqualTo("0.00");assertThat(summary(f).cashPayments().amount()).isEqualTo("100.00");assertThat(summary(f).supplierCredit().amount()).isEqualTo("100.00");
        assertThatThrownBy(()->jdbc.update("UPDATE expense SET gross_amount=0 WHERE id=?",e.id())).isInstanceOf(RuntimeException.class);assertThatThrownBy(()->jdbc.update("DELETE FROM cost_recognition WHERE expense_id=?",e.id())).isInstanceOf(RuntimeException.class);assertThatThrownBy(()->jdbc.update("TRUNCATE treasury_effect CASCADE")).isInstanceOf(RuntimeException.class);
    }
    @Test void internalTransfersAndFinancingNeverBecomeCustomerIncomeOrCost(){
        var f=fixture();var second=treasury.account(f.manager(),key(),new TreasuryService.AccountInput("Destino "+key(),"BANK"));var other=fixture();
        treasury.transfer(f.manager(),key(),new TreasuryService.TransferInput(f.project(),other.project(),f.account(),second.id(),m("100.00"),LocalDate.of(2026,10,7),key(),f.evidence(),"Asignación de fondos"));confirm(f,movement(f,"FINANCING","200.00",null));assertThat(summary(f).confirmedCashReceipts().amount()).isEqualTo("0.00");assertThat(summary(f).validatedCosts().amount()).isEqualTo("0.00");assertThat(summary(f).availableProjectCash().amount()).isEqualTo("100.00");assertThat(summary(other).availableProjectCash().amount()).isEqualTo("100.00");
    }
    @Test void roleProjectionsAndRevocationAreEnforcedOnEveryReadAndCommand()throws Exception{
        var f=fixture();var other=fixture();var d=deposit(f,"100.00");var e=expense(f,"100.00");Actor admin=actor("ADMIN"),supplier=actor("PROVEEDOR");
        mvc.perform(get("/api/v1/projects/"+f.project()+"/client-summary").with(login(f.client()))).andExpect(status().isOk()).andExpect(jsonPath("$.validatedCosts").doesNotExist()).andExpect(jsonPath("$.cashPayments").doesNotExist());
        for(Actor a:List.of(f.client(),f.assistant(),admin,supplier))mvc.perform(get("/api/v1/reports/business-summary").with(login(a))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/projects/"+f.project()+"/financial-summary").with(login(f.client()))).andExpect(status().isForbidden());mvc.perform(get("/api/v1/projects/"+other.project()+"/deposit-reports").with(login(f.client()))).andExpect(status().isNotFound());mvc.perform(get("/api/v1/expenses/"+e.id()).with(login(f.client()))).andExpect(status().isForbidden());
        assertThatThrownBy(()->treasury.confirmDeposit(f.assistant(),f.project(),d.id(),key(),etag(d.version()),new TreasuryService.ConfirmDeposit(null,f.account(),key(),f.evidence(),m("100.00"),"Confirmar"))).isInstanceOf(ApiException.class);
        var p=projects.read(f.manager(),f.project());projects.member(f.manager(),p.id(),key(),etag(p.version()),new ProjectService.Member(f.assistant().id(),"OPERATIONS",null,false,false));mvc.perform(get("/api/v1/projects/"+f.project()+"/financial-summary").with(login(f.assistant()))).andExpect(status().isNotFound());
        var party=parties.read(f.manager(),f.party(),"CLIENT");parties.member(f.manager(),party.id(),"CLIENT",key(),etag(party.version()),new PartyService.Member(f.client().id(),false));mvc.perform(get("/api/v1/projects/"+f.project()+"/deposit-reports/"+d.id()).with(login(f.client()))).andExpect(status().isNotFound());
    }
    @Test void commandsRequireRecentPasswordVersionAndCleanEvidenceAndBlockClosedProjects(){
        var f=fixture();var e=expense(f,"100.00");Actor stale=new Actor(f.manager().id(),f.manager().email(),f.manager().roles(),false,Instant.now().minusSeconds(301));assertThatThrownBy(()->costs.decide(stale,e.id(),key(),etag(e.version()),"validate",new CostService.Decision(null,"Sin factura"))).isInstanceOf(ApiException.class);
        assertThatThrownBy(()->costs.decide(f.manager(),e.id(),key(),null,"validate",new CostService.Decision(null,"Sin factura"))).isInstanceOf(ApiException.class).extracting(x->((ApiException)x).status()).isEqualTo(428);
        var pending=documents.reserve(f.manager(),key(),new DocumentService.Reservation(f.project(),"pendiente.png","image/png",image().length,FileInspection.sha256(image()),"EVIDENCE"));assertThatThrownBy(()->costs.decide(f.manager(),e.id(),key(),etag(e.version()),"validate",new CostService.Decision(pending.document().id(),"Pendiente"))).isInstanceOf(ApiException.class);
        jdbc.update("UPDATE project SET state='CERRADO' WHERE id=?",f.project());assertThatThrownBy(()->validate(f,e)).isInstanceOf(ApiException.class);
    }
    @Test void financialDocumentsCannotLeakThroughClientOrSupplierSharing(){
        var f=fixture();validate(f,expense(f,"100.00"));var doc=documents.read(f.manager(),f.evidence());assertThatThrownBy(()->documents.share(f.manager(),doc.id(),key(),etag(doc.version()),new DocumentService.Sharing("CLIENT",null,"Compartir"))).isInstanceOf(ApiException.class);
    }
    @Test void auditFailureRollsBackCashAndIdempotency() {
        var f=fixture();var m=movement(f,"PAYMENT","100.00",null);String k=key();jdbc.execute("CREATE FUNCTION reject_m2_audit() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.action='MOVEMENT_CONFIRMED' THEN RAISE EXCEPTION 'test'; END IF; RETURN NEW; END $$");jdbc.execute("CREATE TRIGGER reject_m2_audit BEFORE INSERT ON audit_event FOR EACH ROW EXECUTE FUNCTION reject_m2_audit()");
        try{assertThatThrownBy(()->treasury.confirm(f.manager(),m.id(),k,etag(m.version()),new TreasuryService.Decision(f.evidence(),"Confirmar"))).isInstanceOf(RuntimeException.class);assertThat(treasury.read(f.manager(),m.id()).state()).isEqualTo("DRAFT");assertThat(summary(f).cashPayments().amount()).isEqualTo("0.00");assertThat(jdbc.queryForObject("SELECT count(*) FROM idempotency_record WHERE actor_id=? AND key=?",Integer.class,f.manager().id(),k)).isZero();}finally{jdbc.execute("DROP TRIGGER reject_m2_audit ON audit_event");jdbc.execute("DROP FUNCTION reject_m2_audit()");}
    }
    @Test void groupedBusinessSummaryHandlesTwoHundredProjectsAndTenThousandCosts()throws Exception {
        var f=fixture();BigDecimal baseline=reports.business(f.manager(),null).validatedCosts().decimal();String prefix="PERF-"+key().substring(0,8).toUpperCase()+"-";
        jdbc.update("INSERT INTO project(id,code,name,client_id,location,contact_email,starts_on,estimated_ends_on,created_by) SELECT gen_random_uuid(),?||n,'Obra sintética de rendimiento',?,'Sintética','performance@example.invalid','2026-10-01','2026-12-01',? FROM generate_series(1,200) n",prefix,f.party(),f.manager().id());
        jdbc.update("INSERT INTO expense(id,supplier_id,supplier_exception,accrued_on,category,description,currency,net_amount,tax_amount,gross_amount,state,created_by) SELECT gen_random_uuid(),?,'','2026-10-07','MATERIALS',?||n,'CRC',100,13,113,'VALIDATED',? FROM generate_series(1,10000) n",f.supplier(),prefix,f.manager().id());
        jdbc.update("INSERT INTO expense_allocation(id,expense_id,project_id,net_amount,tax_amount,gross_amount) SELECT gen_random_uuid(),e.id,p.id,100,13,113 FROM expense e JOIN project p ON p.code=?||(((substring(e.description,length(?)+1)::int-1)/50)+1) WHERE e.description LIKE ?",prefix,prefix,prefix+"%");
        jdbc.update("INSERT INTO cost_recognition(id,expense_id,allocation_id,project_id,amount,net_amount,tax_amount,effective_on,actor_id,reason) SELECT gen_random_uuid(),a.expense_id,a.id,a.project_id,113,100,13,'2026-10-07',?,'Fixture de rendimiento' FROM expense_allocation a JOIN expense e ON e.id=a.expense_id WHERE e.description LIKE ?",f.manager().id(),prefix+"%");
        assertThat(reports.business(f.manager(),null).validatedCosts().decimal().subtract(baseline)).isEqualByComparingTo("1130000");
        long[] ms=new long[9];for(int i=0;i<ms.length;i++){long begin=System.nanoTime();reports.business(f.manager(),null);ms[i]=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-begin);}Arrays.sort(ms);
        assertThat(ms[8]).isLessThan(2000L);java.nio.file.Files.writeString(java.nio.file.Path.of("target/m2-performance.json"),"{\"projects\":200,\"expenses\":10000,\"samples\":9,\"p95ServiceMs\":"+ms[8]+",\"scope\":\"local PostgreSQL, sin HTTP ni staging\"}");
    }
    @Test void editsOnlyUnvalidatedProposalsAndPreservesAllocationHistoryAndOldCut() {
        var f=fixture();var e=expense(f,"100.00");Instant cut=Instant.now();
        var next=costs.edit(f.assistant(),e.id(),key(),etag(e.version()),expenseInput(f,"200.00","0.00",List.of(new CostService.AllocationInput(f.project(),null,m("200.00"),m("0.00"))),null,null));
        assertThat(next.allocations()).hasSize(1);assertThat(next.total().amount()).isEqualTo("200.00");assertThat(summary(f).registeredCosts().amount()).isEqualTo("200.00");assertThat(reports.project(f.manager(),f.project(),cut).registeredCosts().amount()).isEqualTo("100.00");
        assertThat(costs.read(f.assistant(),next.id()).evidenceDocumentId()).isEqualTo(f.evidence());assertThat(next.supplierException()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_allocation WHERE expense_id=?",Integer.class,e.id())).isEqualTo(2);assertThat(jdbc.queryForObject("SELECT count(*) FROM expense_revision WHERE expense_id=?",Integer.class,e.id())).isEqualTo(2);
        assertThatThrownBy(()->costs.edit(f.manager(),e.id(),key(),etag(e.version()),expenseInput(f,"300.00","0.00",List.of(new CostService.AllocationInput(f.project(),null,m("300.00"),m("0.00"))),null,null))).isInstanceOf(ApiException.class).extracting(x->((ApiException)x).status()).isEqualTo(412);
        var validated=validate(f,next);assertThatThrownBy(()->costs.edit(f.manager(),e.id(),key(),etag(validated.version()),expenseInput(f,"100.00","0.00",List.of(new CostService.AllocationInput(f.project(),null,m("100.00"),m("0.00"))),null,null))).isInstanceOf(ApiException.class);
    }
}
