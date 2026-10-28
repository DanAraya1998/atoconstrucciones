package com.daraya.atoconstrucciones.scheduling;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.contracts.ContractService;
import com.daraya.atoconstrucciones.documents.DocumentService;
import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.projects.ProjectService;
import com.daraya.atoconstrucciones.shared.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.context.event.EventListener;
import org.springframework.transaction.annotation.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.*;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import static com.daraya.atoconstrucciones.scheduling.ScheduleModels.*;

@Service
public class ScheduleService {
    private static final ZoneId ZONE=ZoneId.of("America/Costa_Rica");
    private final JdbcTemplate jdbc;
    private final ProjectService projects;
    private final ContractService contracts;
    private final DocumentService documents;
    private final AuditService audit;
    private final IdempotencyService commands;
    private final SensitiveAuthenticationPolicy sensitive;
    private final JsonMapper json;
    public ScheduleService(JdbcTemplate jdbc,ProjectService projects,ContractService contracts,DocumentService documents,
        AuditService audit,IdempotencyService commands,SensitiveAuthenticationPolicy sensitive,JsonMapper json) {
        this.jdbc=jdbc;this.projects=projects;this.contracts=contracts;this.documents=documents;this.audit=audit;
        this.commands=commands;this.sensitive=sensitive;this.json=json;
    }
    private long revision(UUID p) {
        var rows=jdbc.queryForList("SELECT revision FROM schedule WHERE project_id=?",Long.class,p);return rows.isEmpty()?1:rows.getFirst();
    }
    private List<Task> tasks(UUID p) {
        Map<UUID,List<UUID>> assignments=new HashMap<>();
        jdbc.query("SELECT a.* FROM task_assignment a JOIN schedule_task t ON t.id=a.task_id WHERE t.project_id=? ORDER BY a.supplier_id",r->{assignments.computeIfAbsent(r.getObject("task_id",UUID.class),ignored->new ArrayList<>()).add(r.getObject("supplier_id",UUID.class));},p);
        return jdbc.query("SELECT * FROM schedule_task WHERE project_id=? ORDER BY id",(r,n)->new Task(r.getObject("id",UUID.class),r.getObject("parent_id",UUID.class),r.getString("kind"),r.getString("title"),r.getObject("start_date",LocalDate.class),r.getObject("end_exclusive",LocalDate.class),r.getString("state"),r.getBoolean("administrative"),r.getBoolean("client_visible"),r.getObject("extra_id",UUID.class),assignments.getOrDefault(r.getObject("id",UUID.class),List.of()),r.getBigDecimal("verified_percent").stripTrailingZeros().toPlainString(),r.getObject("verified_on",LocalDate.class),r.getTimestamp("verified_at")==null?null:r.getTimestamp("verified_at").toInstant()),p);
    }
    private List<Dependency> dependencies(UUID p) {
        return jdbc.query("SELECT * FROM task_dependency WHERE project_id=? ORDER BY predecessor_id,successor_id",(r,n)->new Dependency(r.getObject("predecessor_id",UUID.class),r.getObject("successor_id",UUID.class),r.getString("kind"),r.getInt("lag_days")),p);
    }
    private List<Baseline> baselines(UUID p) {
        return jdbc.query("SELECT b.*,a.schedule_revision,a.previous_percent,a.percent FROM progress_baseline b LEFT JOIN progress_baseline_approval a ON a.baseline_id=b.id WHERE b.project_id=? ORDER BY b.created_at DESC,b.id LIMIT 20",(r,n)->{
            UUID id=r.getObject("id",UUID.class);
            var weights=jdbc.query("SELECT * FROM task_weight WHERE baseline_id=? ORDER BY task_id",(w,i)->new Weight(w.getObject("task_id",UUID.class),w.getBigDecimal("weight").stripTrailingZeros().toPlainString(),w.getString("exclusion_reason")),id);
            return new Baseline(id,weights,r.getString("reason"),r.getObject("schedule_revision")==null?"DRAFT":"APPROVED",(Long)r.getObject("schedule_revision"),r.getString("previous_percent"),r.getString("percent"));
        },p);
    }
    private List<Report> reports(UUID p) {
        return jdbc.query("SELECT r.*,v.verified_percent,v.recorded_at AS verified_at FROM progress_report r LEFT JOIN progress_verification v ON v.report_id=r.id WHERE r.project_id=? ORDER BY r.recorded_at DESC,r.id LIMIT 100",(r,n)->new Report(r.getObject("id",UUID.class),r.getObject("task_id",UUID.class),r.getBigDecimal("reported_percent").stripTrailingZeros().toPlainString(),r.getObject("observed_on",LocalDate.class),r.getObject("evidence_id",UUID.class),r.getString("note"),r.getObject("reported_by",UUID.class),r.getTimestamp("recorded_at").toInstant(),r.getBigDecimal("verified_percent")==null?null:r.getBigDecimal("verified_percent").stripTrailingZeros().toPlainString(),r.getTimestamp("verified_at")==null?null:r.getTimestamp("verified_at").toInstant()),p);
    }
    private Schedule load(Actor actor,UUID p){
        var all=tasks(p);var scope=contracts.schedulingScope(actor,p);List<Weight> weights=null;
        var approved=jdbc.queryForList("SELECT b.id,b.scope_hash FROM progress_baseline b JOIN progress_baseline_approval a ON a.baseline_id=b.id WHERE b.project_id=? ORDER BY a.schedule_revision DESC LIMIT 1",p);
        if(!approved.isEmpty()&&scopeHash(all,scope).equals(approved.getFirst().get("scope_hash")))weights=jdbc.query("SELECT * FROM task_weight WHERE baseline_id=?",(r,n)->new Weight(r.getObject("task_id",UUID.class),r.getBigDecimal("weight").toPlainString(),r.getString("exclusion_reason")),approved.getFirst().get("id"));
        return new Schedule(p,revision(p),ScheduleGraph.aggregate(all,weights),dependencies(p),baselines(p),reports(p),all.stream().filter(t->eligible(t,scope)).map(Task::id).toList());
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Schedule read(Actor actor,UUID p){projects.operations(actor,p);return load(actor,p);}
    private long lock(Actor actor,UUID p,String etag) {
        projects.writable(projects.lock(actor,p));
        jdbc.update("INSERT INTO schedule(project_id) VALUES (?) ON CONFLICT DO NOTHING",p);
        long v=jdbc.queryForObject("SELECT revision FROM schedule WHERE project_id=? FOR UPDATE",Long.class,p);
        Versions.check(etag,v);return v;
    }
    private void approve(Actor actor){actor.requireManager();sensitive.require(actor);}
    private static void reason(String value){if(value==null||value.isBlank()||value.length()>1000)Versions.invalid("Indique un motivo de hasta 1000 caracteres.");}
    private BigDecimal percent(String value,int scale){
        if(value==null||!value.matches("[0-9]{1,3}(\\.[0-9]{1,"+scale+"})?"))Versions.invalid("Porcentaje inválido.");
        BigDecimal n=new BigDecimal(value);if(n.compareTo(BigDecimal.valueOf(100))>0)Versions.invalid("Porcentaje fuera de 0 a 100.");return n;
    }
    private Task task(UUID p,UUID id){return tasks(p).stream().filter(t->t.id().equals(id)).findFirst().orElseThrow(ApiException::missing);}
    private void reporter(Actor actor,UUID p,Task t){
        projects.authorize(actor,p);
        if(!actor.manager()&&!projects.capacity(actor,p,"OPERATIONS")&&!projects.supplierAssigned(actor,p,t.supplierIds()))throw ApiException.missing();
    }
    private boolean eligible(Task t,ContractService.SchedulingScope scope){return !t.kind().equals("SUMMARY")&&!t.state().equals("CANCELLED")&&(t.extraId()==null||scope.extraIds().contains(t.extraId()));}
    private String scopeHash(List<Task> tasks,ContractService.SchedulingScope scope){
        var shape=tasks.stream().filter(t->eligible(t,scope)).sorted(Comparator.comparing(Task::id)).map(t->Arrays.asList(t.id(),t.parentId(),t.kind(),t.title(),t.startDate(),t.endExclusive(),t.administrative(),t.clientVisible(),t.extraId())).toList();
        return IdempotencyService.hash(json.writeValueAsString(Arrays.asList(shape,scope)));
    }
    private void coverage(List<Task> tasks,ContractService.SchedulingScope scope,List<Weight> weights){
        Map<UUID,Task> relevant=new HashMap<>();tasks.stream().filter(t->eligible(t,scope)).forEach(t->relevant.put(t.id(),t));
        if(relevant.isEmpty())Versions.invalid("Defina entregables antes de aprobar los pesos.");
        Set<UUID> seen=new HashSet<>();BigDecimal sum=BigDecimal.ZERO;
        for(Weight w:weights){
            Task t=relevant.get(w.taskId());if(t==null||!seen.add(w.taskId()))Versions.invalid("Ponderación repetida o fuera del alcance entregable vigente.");
            BigDecimal weight=percent(w.weight(),6);
            if(t.administrative()) {if(weight.signum()!=0||w.exclusionReason()==null||w.exclusionReason().isBlank())Versions.invalid("Una tarea administrativa requiere peso cero y exclusión motivada.");}
            else {if(weight.signum()<=0)Versions.invalid("Todo entregable requiere un peso positivo.");sum=sum.add(weight);}
        }
        if(!seen.equals(relevant.keySet())||sum.compareTo(BigDecimal.valueOf(100))!=0)Versions.invalid("Cubra todas las hojas/hitos vigentes; los pesos deben sumar exactamente 100.");
        for(UUID extra:scope.extraIds())if(relevant.values().stream().noneMatch(t->extra.equals(t.extraId())&&!t.administrative()))Versions.invalid("Cada extra aprobado requiere entregables ponderados.");
    }
    private String weighted(List<Task> tasks,List<Weight> weights){
        Map<UUID,Task> byId=new HashMap<>();tasks.forEach(t->byId.put(t.id(),t));BigDecimal sum=BigDecimal.ZERO;
        for(Weight w:weights)sum=sum.add(new BigDecimal(w.weight()).multiply(new BigDecimal(byId.get(w.taskId()).verifiedPercent())));
        return sum.divide(BigDecimal.valueOf(100),2,RoundingMode.HALF_UP).toPlainString();
    }
    private ProgressSummary summary(Actor actor,UUID p,Instant cut){
        var all=tasks(p);var scope=contracts.schedulingScope(actor,p);String hash=scopeHash(all,scope);
        var approval=jdbc.queryForList("SELECT b.id,b.scope_hash,a.schedule_revision,a.reason,a.previous_percent FROM progress_baseline b JOIN progress_baseline_approval a ON a.baseline_id=b.id WHERE b.project_id=? ORDER BY a.schedule_revision DESC LIMIT 1",p);
        String value=null,cause="Avance pendiente de definición: se requiere una revisión aprobada con cobertura completa.",previous=null;Long baselineRevision=null;
        if(!approval.isEmpty()){
            var row=approval.getFirst();baselineRevision=((Number)row.get("schedule_revision")).longValue();previous=(String)row.get("previous_percent");cause=(String)row.get("reason");
            if(hash.equals(row.get("scope_hash"))){
                var weights=jdbc.query("SELECT * FROM task_weight WHERE baseline_id=? ORDER BY task_id",(r,n)->new Weight(r.getObject("task_id",UUID.class),r.getBigDecimal("weight").toPlainString(),r.getString("exclusion_reason")),row.get("id"));
                coverage(all,scope,weights);value=weighted(all,weights);
            }else cause="El alcance o las fechas cambiaron. Se requiere revisar y aprobar los pesos; se conserva el corte anterior.";
        }
        LocalDate on=cut.atZone(ZONE).toLocalDate();
        boolean published=value!=null;
        var milestones=all.stream().filter(t->published&&eligible(t,scope)&&t.clientVisible()).map(t->new Milestone(t.title(),t.startDate(),t.verifiedPercent(),t.startDate().isBefore(on)&&new BigDecimal(t.verifiedPercent()).compareTo(BigDecimal.valueOf(100))<0)).toList();
        Instant verified=all.stream().filter(t->eligible(t,scope)&&!t.administrative()).map(Task::verifiedAt).filter(Objects::nonNull).max(Instant::compareTo).orElse(null);
        var project=projects.read(actor,p);
        return new ProgressSummary(value==null?"PENDING_DEFINITION":"VERIFIED",value,baselineRevision,revision(p),cut,verified,project.estimatedEndsOn(),cause,previous,milestones);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public ProgressSummary progress(Actor actor,UUID p,Instant asOf){
        projects.authorize(actor,p);if(!actor.manager()&&!projects.capacity(actor,p,"OPERATIONS")&&!projects.capacity(actor,p,"CLIENT"))throw ApiException.forbidden();
        if(asOf==null)return summary(actor,p,Instant.now());
        if(asOf.isAfter(Instant.now()))Versions.invalid("El corte no puede ser futuro.");
        var rows=jdbc.queryForList("SELECT client_snapshot::text FROM schedule_revision WHERE project_id=? AND recorded_at<=? ORDER BY revision DESC LIMIT 1",String.class,p,Timestamp.from(asOf));
        if(rows.isEmpty())return new ProgressSummary("PENDING_DEFINITION",null,null,1,asOf,null,null,"No había avance aprobado en ese corte.",null,List.of());
        var old=json.readValue(rows.getFirst(),ProgressSummary.class);
        var milestones=old.milestones().stream().map(m->new Milestone(m.title(),m.estimatedOn(),m.verifiedPercent(),m.estimatedOn().isBefore(asOf.atZone(ZONE).toLocalDate())&&new BigDecimal(m.verifiedPercent()).compareTo(BigDecimal.valueOf(100))<0)).toList();
        return new ProgressSummary(old.status(),old.percent(),old.baselineRevision(),old.scheduleRevision(),asOf,old.lastVerifiedAt(),old.estimatedEndsOn(),old.reason(),old.previousPercent(),milestones);
    }
    private Schedule commit(Actor actor,UUID p,String action,String reason){
        jdbc.update("UPDATE schedule SET revision=revision+1 WHERE project_id=?",p);Schedule s=load(actor,p);var view=summary(actor,p,Instant.now());
        jdbc.update("INSERT INTO schedule_revision(project_id,revision,snapshot,client_snapshot,reason,actor_id) VALUES (?,?,?::jsonb,?::jsonb,?,?)",p,s.revision(),json.writeValueAsString(s),json.writeValueAsString(view),reason,actor.id());
        audit.record(actor.id(),action,p,Map.of("scheduleRevision",s.revision(),"reason",reason));return s;
    }
    @EventListener
    public void contractScopeChanged(ContractService.ScopeChanged event){externalRevision(event.actor(),event.projectId(),"SCHEDULE_SCOPE_CHANGED","Cambio de alcance contractual; revise los entregables y sus pesos.");}
    @EventListener
    public void projectPlanningChanged(ProjectService.PlanningChanged event){externalRevision(event.actor(),event.projectId(),"SCHEDULE_PROJECT_INFO_CHANGED","Actualización de la ficha y fechas estimadas de obra.");}
    private void externalRevision(Actor actor,UUID p,String action,String reason){
        // El emisor ya mantiene el guard de Project. Listener síncrono: snapshot/auditoría en el mismo commit.
        var rows=jdbc.queryForList("SELECT revision FROM schedule WHERE project_id=? FOR UPDATE",Long.class,p);
        if(!rows.isEmpty())commit(actor,p,action,reason);
    }
    @Transactional
    public Schedule change(Actor actor,UUID p,String key,String etag,Changes input){
        projects.operations(actor,p);reason(input.reason());
        return commands.execute(actor.id(),"POST /projects/"+p+"/schedule/changes",key,Arrays.asList(input,etag),Schedule.class,()->{
            long v=lock(actor,p,etag);projects.operations(actor,p);if(v!=input.baseRevision())throw new ApiException(412,"VERSION_MISMATCH","La revisión base del cronograma cambió.");
            Map<UUID,Task> old=new LinkedHashMap<>();tasks(p).forEach(t->old.put(t.id(),t));Set<UUID> supplied=new HashSet<>();List<Task> next=new ArrayList<>();
            Set<UUID> suppliers=new HashSet<>(projects.assignedSuppliers(actor,p));
            for(TaskInput i:input.tasks()){
                if(!supplied.add(i.id()))Versions.invalid("Tarea repetida en el lote.");
                Task prior=old.get(i.id());
                if(prior==null&&Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM schedule_task WHERE id=?)",Boolean.class,i.id())))throw ApiException.missing();
                if(!suppliers.containsAll(i.supplierIds()))Versions.invalid("Seleccione proveedores activos asignados a esta obra.");
                if(i.extraId()!=null)contracts.readExtra(actor,p,i.extraId());
                if(prior!=null&&(!prior.kind().equals(i.kind())||!Objects.equals(prior.extraId(),i.extraId())))Versions.invalid("Tipo y extra conservan identidad; retire la tarea y cree otra.");
                if(i.state().equals("COMPLETED")&&(prior==null||!prior.verifiedPercent().equals("100")))Versions.conflict("Completar requiere avance verificado 100 y evidencia.");
                if(i.state().equals("COMPLETED")&&i.kind().equals("SUMMARY"))Versions.invalid("Las etapas agregan avance; complete sus entregables.");
                if(prior!=null&&prior.state().equals("COMPLETED")&&!i.state().equals("COMPLETED"))approve(actor);
                if(prior!=null&&prior.state().equals("CANCELLED")&&!i.state().equals("CANCELLED"))Versions.conflict("Una tarea retirada conserva su historial; cree una nueva tarea.");
                next.add(new Task(i.id(),i.parentId(),i.kind(),i.title().trim(),i.startDate(),i.endExclusive(),i.state(),i.administrative(),i.clientVisible(),i.extraId(),i.supplierIds(),prior==null?"0":prior.verifiedPercent(),prior==null?null:prior.verifiedOn(),prior==null?null:prior.verifiedAt()));
            }
            // La omisión no destruye historia ni retira silenciosamente tareas existentes.
            if(!supplied.containsAll(old.keySet()))Versions.invalid("Incluya todas las tareas; retire mediante estado CANCELLED.");
            next=ScheduleGraph.validate(next,input.dependencies());
            jdbc.update("DELETE FROM task_dependency WHERE project_id=?",p);
            jdbc.update("DELETE FROM task_assignment WHERE task_id IN (SELECT id FROM schedule_task WHERE project_id=?)",p);
            for(Task t:next){
                int written=jdbc.update("INSERT INTO schedule_task(id,project_id,parent_id,kind,title,start_date,end_exclusive,state,administrative,client_visible,extra_id) VALUES (?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET parent_id=excluded.parent_id,title=excluded.title,start_date=excluded.start_date,end_exclusive=excluded.end_exclusive,state=excluded.state,administrative=excluded.administrative,client_visible=excluded.client_visible WHERE schedule_task.project_id=excluded.project_id",t.id(),p,t.parentId(),t.kind(),t.title(),t.startDate(),t.endExclusive(),t.state(),t.administrative(),t.clientVisible(),t.extraId());
                if(written!=1)throw ApiException.missing();
                for(UUID supplier:t.supplierIds())jdbc.update("INSERT INTO task_assignment(task_id,supplier_id) VALUES (?,?)",t.id(),supplier);
            }
            for(Dependency d:input.dependencies())jdbc.update("INSERT INTO task_dependency(project_id,predecessor_id,successor_id,kind,lag_days) VALUES (?,?,?,?,?)",p,d.predecessorId(),d.successorId(),d.kind(),d.lagDays());
            return commit(actor,p,"SCHEDULE_CHANGED",input.reason());
        });
    }
    @Transactional
    public Schedule createBaseline(Actor actor,UUID p,String key,String etag,BaselineInput input){
        approve(actor);projects.operations(actor,p);reason(input.reason());
        return commands.execute(actor.id(),"POST /projects/"+p+"/schedule/progress-baselines",key,Arrays.asList(input,etag),Schedule.class,()->{
            lock(actor,p,etag);var all=tasks(p);var scope=contracts.schedulingScope(actor,p);UUID id=UUID.randomUUID();
            Set<UUID> ids=new HashSet<>();for(Weight w:input.weights()) {percent(w.weight(),6);if(!ids.add(w.taskId())||all.stream().noneMatch(t->t.id().equals(w.taskId())))Versions.invalid("Tarea repetida o ajena en la revisión de pesos.");}
            jdbc.update("INSERT INTO progress_baseline(id,project_id,scope_hash,scope_snapshot,reason,created_by) VALUES (?,?,?,?::jsonb,?,?)",id,p,scopeHash(all,scope),json.writeValueAsString(Arrays.asList(all,scope)),input.reason(),actor.id());
            for(Weight w:input.weights())jdbc.update("INSERT INTO task_weight(baseline_id,task_id,weight,exclusion_reason) VALUES (?,?,?,?)",id,w.taskId(),new BigDecimal(w.weight()),w.exclusionReason());
            return commit(actor,p,"PROGRESS_BASELINE_CREATED",input.reason());
        });
    }
    @Transactional
    public Schedule approveBaseline(Actor actor,UUID p,UUID id,String key,String etag,Decision input){
        approve(actor);projects.operations(actor,p);reason(input.reason());
        return commands.execute(actor.id(),"POST /projects/"+p+"/schedule/progress-baselines/"+id+"/approve",key,Arrays.asList(input,etag),Schedule.class,()->{
            long v=lock(actor,p,etag);var all=tasks(p);var scope=contracts.schedulingScope(actor,p);
            var rows=jdbc.queryForList("SELECT scope_hash FROM progress_baseline WHERE project_id=? AND id=?",String.class,p,id);if(rows.isEmpty())throw ApiException.missing();
            if(!scopeHash(all,scope).equals(rows.getFirst()))Versions.conflict("El alcance cambió desde la propuesta de pesos. Prepare otra revisión.");
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM progress_baseline_approval WHERE baseline_id=?)",Boolean.class,id)))Versions.conflict("La revisión ya fue aprobada.");
            var weights=jdbc.query("SELECT * FROM task_weight WHERE baseline_id=? ORDER BY task_id",(r,n)->new Weight(r.getObject("task_id",UUID.class),r.getBigDecimal("weight").toPlainString(),r.getString("exclusion_reason")),id);
            coverage(all,scope,weights);
            var previous=jdbc.queryForList("SELECT client_snapshot->>'percent' FROM schedule_revision WHERE project_id=? AND client_snapshot->>'percent' IS NOT NULL ORDER BY revision DESC LIMIT 1",String.class,p);
            jdbc.update("INSERT INTO progress_baseline_approval(baseline_id,project_id,schedule_revision,approved_by,reason,previous_percent,percent) VALUES (?,?,?,?,?,?,?)",id,p,v+1,actor.id(),input.reason(),previous.isEmpty()?null:previous.getFirst(),weighted(all,weights));
            return commit(actor,p,"PROGRESS_BASELINE_APPROVED",input.reason());
        });
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public AssignedTasks assigned(Actor actor,UUID p){
        projects.authorize(actor,p);if(!projects.capacity(actor,p,"SUPPLIER"))throw ApiException.forbidden();
        var all=tasks(p);var deps=dependencies(p);Map<UUID,Task> byId=new HashMap<>();all.forEach(t->byId.put(t.id(),t));
        var visible=all.stream().filter(t->projects.supplierAssigned(actor,p,t.supplierIds())&&!t.state().equals("CANCELLED")).toList();Set<UUID> ids=new HashSet<>();visible.forEach(t->ids.add(t.id()));
        var projected=visible.stream().map(t->new AssignedTask(t.id(),t.title(),t.kind(),t.startDate(),t.endExclusive(),t.state(),t.verifiedPercent(),deps.stream().filter(d->d.successorId().equals(t.id())).map(d->byId.get(d.predecessorId()).endExclusive().plusDays(d.lagDays())).max(LocalDate::compareTo).orElse(null))).toList();
        return new AssignedTasks(revision(p),projected,reports(p).stream().filter(r->ids.contains(r.taskId())&&r.reportedBy().equals(actor.id())).toList());
    }
    @Transactional
    public AssignedTasks report(Actor actor,UUID p,UUID taskId,String key,String etag,ReportInput input){
        Task initial=task(p,taskId);reporter(actor,p,initial);reason(input.note());BigDecimal n=percent(input.percent(),4);
        if(input.observedOn()==null||input.observedOn().isAfter(LocalDate.now(ZONE)))Versions.invalid("La fecha observada no puede ser futura.");
        return commands.execute(actor.id(),"POST /projects/"+p+"/schedule/tasks/"+taskId+"/progress-reports",key,Arrays.asList(input,etag),AssignedTasks.class,()->{
            lock(actor,p,etag);Task t=task(p,taskId);reporter(actor,p,t);
            if(t.kind().equals("SUMMARY")||t.state().equals("CANCELLED"))Versions.conflict("Reporte solo entregables vigentes.");
            if(t.kind().equals("MILESTONE")&&n.signum()!=0&&n.compareTo(BigDecimal.valueOf(100))!=0)Versions.invalid("Un hito admite 0 o 100 según aceptación.");
            documents.retain(actor,p,input.evidenceId(),"PROGRESS_REPORT");
            jdbc.update("INSERT INTO progress_report(id,project_id,task_id,reported_by,reported_percent,observed_on,evidence_id,note) VALUES (?,?,?,?,?,?,?,?)",UUID.randomUUID(),p,taskId,actor.id(),n,input.observedOn(),input.evidenceId(),input.note());
            commit(actor,p,"PROGRESS_REPORTED",input.note());
            if(actor.manager()||projects.capacity(actor,p,"OPERATIONS"))return new AssignedTasks(revision(p),List.of(),reports(p));
            return assigned(actor,p);
        });
    }
    @Transactional
    public Schedule verify(Actor actor,UUID p,UUID reportId,String key,String etag,Verification input){
        approve(actor);projects.operations(actor,p);reason(input.reason());BigDecimal n=percent(input.percent(),4);
        return commands.execute(actor.id(),"POST /projects/"+p+"/schedule/progress-reports/"+reportId+"/verify",key,Arrays.asList(input,etag),Schedule.class,()->{
            lock(actor,p,etag);var rows=jdbc.queryForList("SELECT * FROM progress_report WHERE project_id=? AND id=?",p,reportId);if(rows.isEmpty())throw ApiException.missing();var r=rows.getFirst();
            if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM progress_verification WHERE report_id=?)",Boolean.class,reportId)))Versions.conflict("El reporte ya fue verificado; registre una corrección con otro reporte.");
            Task t=task(p,(UUID)r.get("task_id"));LocalDate on=((java.sql.Date)r.get("observed_on")).toLocalDate();
            if(t.state().equals("CANCELLED"))Versions.conflict("La tarea está retirada.");
            if(t.verifiedOn()!=null&&on.isBefore(t.verifiedOn()))Versions.conflict("El reporte es anterior al último corte verificado.");
            if(t.kind().equals("MILESTONE")&&n.signum()!=0&&n.compareTo(BigDecimal.valueOf(100))!=0)Versions.invalid("Un hito admite 0 o 100.");
            documents.retain(actor,p,input.evidenceId(),"PROGRESS_VERIFIED");
            jdbc.update("INSERT INTO progress_verification(report_id,verified_percent,evidence_id,reason,verified_by) VALUES (?,?,?,?,?)",reportId,n,input.evidenceId(),input.reason(),actor.id());
            jdbc.update("UPDATE schedule_task SET verified_percent=?,verified_on=?,verified_at=clock_timestamp(),state=CASE WHEN ?=100 THEN 'COMPLETED' WHEN ?=0 THEN 'PLANNED' ELSE 'IN_PROGRESS' END WHERE id=?",n,on,n,n,t.id());
            return commit(actor,p,"PROGRESS_VERIFIED",input.reason());
        });
    }
}
