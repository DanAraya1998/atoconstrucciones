package com.daraya.atoconstrucciones.finance;

import com.daraya.atoconstrucciones.documents.DocumentService;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.identity.SensitiveAuthenticationPolicy;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.shared.*;
import org.springframework.stereotype.Service;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;

@Service
public class FinancePolicy {
    public static final ZoneId ZONE=ZoneId.of("America/Costa_Rica");
    private final ProjectService projects;
    private final DocumentService documents;
    private final SensitiveAuthenticationPolicy sensitive;
    public FinancePolicy(ProjectService projects,DocumentService documents,SensitiveAuthenticationPolicy sensitive) {
        this.projects=projects; this.documents=documents; this.sensitive=sensitive;
    }
    public void approve(Actor actor) { actor.requireManager(); sensitive.require(actor); }
    public void operations(Actor actor,UUID project) { projects.operations(actor,project); }
    public void collections(Actor actor,UUID project) {
        projects.authorize(actor,project);
        if(!actor.manager()&&!projects.capacity(actor,project,"OPERATIONS")&&!projects.capacity(actor,project,"CLIENT")) throw ApiException.forbidden();
    }
    public void lock(Actor actor,Collection<UUID> ids,boolean clientAllowed) {
        for(UUID id:ids.stream().distinct().sorted().toList()) {
            if(clientAllowed) collections(actor,id); else operations(actor,id);
            projects.writable(projects.lock(actor,id));
            if(clientAllowed) collections(actor,id); else operations(actor,id);
        }
    }
    public void evidence(Actor actor,UUID project,UUID document,String exception,String purpose) {
        if(document!=null) documents.retain(actor,project,document,purpose);
        else { actor.requireManager(); reason(exception); }
    }
    public void evidenceAcross(Actor actor,Collection<UUID> projects,UUID document,String exception,String purpose) {
        UUID project=projects.iterator().next();
        if(document!=null) {project=documents.read(actor,document).projectId();if(!projects.contains(project))throw ApiException.missing();}
        evidence(actor,project,document,exception,purpose);
    }
    public static BigDecimal amount(Money money,int scale,boolean positive) {
        if(money==null||!"CRC".equals(money.currency())) Versions.invalid("M2 admite CRC; otras monedas requieren conversión en BK-037.");
        BigDecimal value=money.decimal();
        if(value.scale()>scale||value.precision()-value.scale()>18||value.signum()<0||positive&&value.signum()==0) Versions.invalid("Importe o precisión inválidos.");
        return value;
    }
    public static Money money(BigDecimal value) { return new Money(value.setScale(Math.max(2,value.stripTrailingZeros().scale())).toPlainString(),"CRC"); }
    public static void date(LocalDate value) { if(value==null||value.isAfter(LocalDate.now(ZONE))) Versions.invalid("La fecha de operación o devengo no puede ser futura."); }
    public static void reason(String reason) { if(reason==null||reason.isBlank()||reason.length()>1000) Versions.invalid("Indique un motivo de hasta 1000 caracteres."); }
    public static Instant cutoff(Instant asOf) { Instant value=asOf==null?Instant.now():asOf; if(value.isAfter(Instant.now().plusSeconds(1))) Versions.invalid("El corte no puede ser futuro."); return value; }
}
