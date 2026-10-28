package com.daraya.atoconstrucciones.projects;

import com.daraya.atoconstrucciones.contracts.ContractService;
import com.daraya.atoconstrucciones.documents.DocumentService;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.reporting.ReportingService;
import com.daraya.atoconstrucciones.scheduling.ScheduleService;
import com.daraya.atoconstrucciones.scheduling.ScheduleModels.ProgressSummary;
import com.daraya.atoconstrucciones.shared.*;
import com.daraya.atoconstrucciones.treasury.TreasuryService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.time.LocalDate;
import java.util.*;

/** Proyección explícita: referencias de trámites propios, sin actores ni movimientos de caja internos. */
@Service
public class ClientPortalService {
    public record ProjectCard(UUID id,String code,String name,String location,String state,LocalDate startsOn,LocalDate estimatedEndsOn) {}
    public record ClientProjects(List<ProjectCard> items,int page,int size) {}
    public record ClientContract(String number,String scope,Money initialTotal,Money currentTotal,LocalDate currentEndsOn,String state) {}
    public record ExtraQuote(int revision,String scope,Money net,Money tax,int daysDelta,boolean accepted) {}
    public record ClientExtra(UUID id,String scope,String state,long version,List<ExtraQuote> revisions) {}
    public record ClientDeposit(UUID id,Money money,LocalDate operationDate,String paymentMethod,String bankReference,String note,String state) {}
    public record ClientDocument(UUID id,String filename,String state) {}
    public record Portal(ProjectCard project,ClientContract contract,ReportingService.ClientSummary finance,ProgressSummary progress,
        List<ClientExtra> extras,List<ClientDeposit> deposits,int depositPage,List<ClientDocument> documents,boolean representative) {}
    private final ProjectService projects;private final ContractService contracts;private final TreasuryService treasury;
    private final ReportingService reporting;private final ScheduleService schedules;private final DocumentService documents;
    public ClientPortalService(ProjectService projects,ContractService contracts,TreasuryService treasury,ReportingService reporting,ScheduleService schedules,DocumentService documents){
        this.projects=projects;this.contracts=contracts;this.treasury=treasury;this.reporting=reporting;this.schedules=schedules;this.documents=documents;
    }
    private void authorize(Actor actor,UUID p){projects.authorize(actor,p);if(!projects.capacity(actor,p,"CLIENT"))throw ApiException.forbidden();}
    private ClientExtra extra(ContractService.Extra e){return new ClientExtra(e.id(),e.scope(),e.state(),e.version(),e.revisions().stream().map(r->new ExtraQuote(r.revision(),r.scope(),r.net(),r.tax(),r.daysDelta(),r.acceptanceEvidence()!=null)).toList());}
    private ClientDeposit deposit(TreasuryService.Deposit d){return new ClientDeposit(d.id(),d.money(),d.operationDate(),d.paymentMethod(),d.bankReference(),d.note(),d.state());}
    public ClientProjects list(Actor actor,int page,int size){var result=projects.clientList(actor,page,size);return new ClientProjects(result.items().stream().map(p->new ProjectCard(p.id(),p.code(),p.name(),p.location(),p.state(),p.startsOn(),p.estimatedEndsOn())).toList(),page,size);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Portal read(Actor actor,UUID p,int depositPage){
        authorize(actor,p);var project=projects.read(actor,p);ClientContract contract=null;
        try{var c=contracts.read(actor,p,null);contract=new ClientContract(c.number(),c.scope(),c.initialTotal(),c.currentTotal(),c.currentEndsOn(),c.state());}catch(ApiException e){if(e.status()!=404)throw e;}
        boolean representative=true;try{projects.representative(actor,p);}catch(ApiException e){representative=false;}
        return new Portal(new ProjectCard(project.id(),project.code(),project.name(),project.location(),project.state(),project.startsOn(),project.estimatedEndsOn()),contract,reporting.client(actor,p,null),schedules.progress(actor,p,null),
            contracts.extras(actor,p).items().stream().map(this::extra).toList(),treasury.deposits(actor,p,depositPage,20).stream().map(this::deposit).toList(),depositPage,
            documents.list(actor,p).items().stream().map(d->new ClientDocument(d.id(),d.filename(),d.state())).toList(),representative);
    }
    @Transactional public ClientDeposit report(Actor actor,UUID p,String key,TreasuryService.DepositInput input){authorize(actor,p);return deposit(treasury.report(actor,p,key,input));}
    @Transactional public ClientExtra request(Actor actor,UUID p,String key,ContractService.Request input){authorize(actor,p);return extra(contracts.request(actor,p,key,input));}
    @Transactional public ClientExtra accept(Actor actor,UUID p,UUID id,String key,String etag,ContractService.Evidence input){authorize(actor,p);return extra(contracts.accept(actor,p,id,key,etag,input));}
}
