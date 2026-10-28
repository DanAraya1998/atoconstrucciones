package com.daraya.atoconstrucciones.scheduling;

import com.daraya.atoconstrucciones.shared.Versions;
import java.util.*;
import java.time.LocalDate;
import java.math.*;
import static com.daraya.atoconstrucciones.scheduling.ScheduleModels.*;

/** Validación lineal del grafo completo. El componente visual no gobierna las fechas. */
public final class ScheduleGraph {
    private ScheduleGraph() {}
    public static List<Task> aggregate(List<Task> tasks,List<Weight> weights) {
        Map<UUID,Task> byId=new HashMap<>();tasks.forEach(t->byId.put(t.id(),t));
        return tasks.stream().map(t->{
            if(!t.kind().equals("SUMMARY"))return t;
            BigDecimal sum=BigDecimal.ZERO,total=BigDecimal.ZERO;
            if(weights!=null)for(Weight w:weights){
                Task leaf=byId.get(w.taskId());if(leaf==null)continue;
                UUID parent=leaf.parentId();boolean descendant=false;
                for(int i=0;parent!=null&&i<tasks.size();i++){if(parent.equals(t.id())){descendant=true;break;}Task p=byId.get(parent);parent=p==null?null:p.parentId();}
                if(descendant){BigDecimal weight=new BigDecimal(w.weight());total=total.add(weight);sum=sum.add(weight.multiply(new BigDecimal(leaf.verifiedPercent())));}
            }
            String percent=total.signum()==0?null:sum.divide(total,2,RoundingMode.HALF_UP).toPlainString();
            return new Task(t.id(),t.parentId(),t.kind(),t.title(),t.startDate(),t.endExclusive(),t.state(),t.administrative(),t.clientVisible(),t.extraId(),t.supplierIds(),percent,null,null);
        }).toList();
    }
    public static List<Task> validate(List<Task> input,List<Dependency> dependencies) {
        if(input.size()>500||dependencies.size()>2000)Versions.invalid("El plan excede 500 tareas o 2000 dependencias.");
        Map<UUID,Task> tasks=new LinkedHashMap<>();
        Map<UUID,List<UUID>> hierarchy=new HashMap<>();
        for(Task t:input) {
            if(t.id()==null||tasks.put(t.id(),t)!=null)Versions.invalid("Identificador de tarea repetido.");
            if(t.title()==null||t.title().isBlank()||t.title().length()>160)Versions.invalid("Nombre de tarea inválido.");
            if(!Set.of("LEAF","SUMMARY","MILESTONE").contains(t.kind()))Versions.invalid("Tipo de tarea inválido.");
            if(!Set.of("PLANNED","IN_PROGRESS","BLOCKED","COMPLETED","CANCELLED").contains(t.state()))Versions.invalid("Estado de tarea inválido.");
            if(new HashSet<>(t.supplierIds()).size()!=t.supplierIds().size())Versions.invalid("Asignación repetida.");
            if(!t.kind().equals("SUMMARY")&&(t.startDate()==null||t.endExclusive()==null||
                (t.kind().equals("LEAF")?!t.startDate().isBefore(t.endExclusive()):!t.startDate().equals(t.endExclusive()))))Versions.invalid("Intervalo inválido: hoja con fin exclusivo positivo; hito con duración cero.");
            if(t.kind().equals("SUMMARY")&&(!t.supplierIds().isEmpty()||t.extraId()!=null||t.clientVisible()))Versions.invalid("Las etapas solo agregan tareas; no reciben asignaciones ni visibilidad de hito.");
            if(t.clientVisible()&&(!t.kind().equals("MILESTONE")||t.administrative()))Versions.invalid("Solo los hitos entregables pueden compartirse con cliente.");
        }
        for(Task t:input)if(t.parentId()!=null){
            Task parent=tasks.get(t.parentId());
            if(parent==null||!parent.kind().equals("SUMMARY")||t.id().equals(t.parentId()))Versions.invalid("El padre debe ser una etapa de esta obra.");
            if(!t.state().equals("CANCELLED")&&parent.state().equals("CANCELLED"))Versions.invalid("Retire también los hijos de una etapa retirada.");
            hierarchy.computeIfAbsent(t.parentId(),ignored->new ArrayList<>()).add(t.id());
        }
        List<UUID> order=topological(tasks.keySet(),hierarchy,"La jerarquía contiene un ciclo.");
        Collections.reverse(order);
        for(UUID id:order){Task t=tasks.get(id);if(t.kind().equals("SUMMARY")){
            List<Task> children=hierarchy.getOrDefault(id,List.of()).stream().map(tasks::get).filter(c->!c.state().equals("CANCELLED")).toList();
            LocalDate start=children.stream().map(Task::startDate).filter(Objects::nonNull).min(LocalDate::compareTo).orElse(null);
            LocalDate end=children.stream().map(Task::endExclusive).filter(Objects::nonNull).max(LocalDate::compareTo).orElse(null);
            tasks.put(id,new Task(t.id(),t.parentId(),t.kind(),t.title(),start,end,t.state(),t.administrative(),false,null,List.of(),t.verifiedPercent(),t.verifiedOn(),t.verifiedAt()));
        }}
        Map<UUID,List<UUID>> graph=new HashMap<>();Set<String> unique=new HashSet<>();
        for(Dependency d:dependencies){Task a=tasks.get(d.predecessorId()),b=tasks.get(d.successorId());
            if(a==null||b==null||a.kind().equals("SUMMARY")||b.kind().equals("SUMMARY")||a.state().equals("CANCELLED")||b.state().equals("CANCELLED"))Versions.invalid("Dependencias solo entre tareas programables vigentes de esta obra.");
            if(!"FS".equals(d.kind())||d.lagDays()<0||d.lagDays()>3650)Versions.invalid("Solo FS y desfases de 0 a 3650 días calendario.");
            if(!unique.add(d.predecessorId()+":"+d.successorId()))Versions.invalid("Dependencia repetida.");
            graph.computeIfAbsent(a.id(),ignored->new ArrayList<>()).add(b.id());
        }
        topological(tasks.keySet(),graph,"Las dependencias contienen un ciclo.");
        for(Dependency d:dependencies){Task predecessor=tasks.get(d.predecessorId()),successor=tasks.get(d.successorId());LocalDate earliest=predecessor.endExclusive().plusDays(d.lagDays());
            if(successor.startDate().isBefore(earliest))Versions.invalid("Conflicto FS: «"+successor.title()+"» debe iniciar el "+earliest+" o después por su dependencia de «"+predecessor.title()+"».");
        }
        return List.copyOf(tasks.values());
    }
    private static List<UUID> topological(Set<UUID> ids,Map<UUID,List<UUID>> graph,String error){
        Map<UUID,Integer> degree=new HashMap<>();ids.forEach(id->degree.put(id,0));
        graph.values().forEach(edges->edges.forEach(id->degree.merge(id,1,Integer::sum)));
        ArrayDeque<UUID> ready=new ArrayDeque<>();degree.forEach((id,n)->{if(n==0)ready.add(id);});List<UUID> result=new ArrayList<>();
        while(!ready.isEmpty()){UUID id=ready.remove();result.add(id);for(UUID next:graph.getOrDefault(id,List.of()))if(degree.merge(next,-1,Integer::sum)==0)ready.add(next);}
        if(result.size()!=ids.size())Versions.invalid(error);return result;
    }
}
