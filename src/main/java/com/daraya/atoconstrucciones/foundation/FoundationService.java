package com.daraya.atoconstrucciones.foundation;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.identity.*;
import com.daraya.atoconstrucciones.shared.*;
import com.daraya.atoconstrucciones.shared.jobs.JobService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

/** Recurso sintético de aceptación M0; no representa proyectos ni hechos financieros. */
@Service
public class FoundationService {
    public record Resource(UUID id, String name, long version) {}
    public record Command(String name, String expectedVersion) {}
    public record Queued(UUID id, String state) {}
    public record Page(List<Resource> items, int page, int size) {}
    private final JdbcTemplate jdbc;
    private final IdentityService identity;
    private final SupportService support;
    private final AuditService audit;
    private final IdempotencyService idempotency;
    private final JobService jobs;
    public FoundationService(JdbcTemplate jdbc, IdentityService identity, SupportService support,
                             AuditService audit, IdempotencyService idempotency, JobService jobs) {
        this.jdbc=jdbc; this.identity=identity; this.support=support; this.audit=audit; this.idempotency=idempotency; this.jobs=jobs;
    }
    private Resource load(UUID id, boolean lock) {
        var rows = jdbc.query("SELECT id,name,version FROM foundation_resource WHERE id=?" + (lock ? " FOR UPDATE" : ""),
                (rs,n) -> new Resource(rs.getObject("id",UUID.class), rs.getString("name"), rs.getLong("version")), id);
        if (rows.isEmpty()) throw ApiException.missing(); return rows.getFirst();
    }
    private boolean operational(Actor actor) {
        return actor.roles().stream().anyMatch(role -> Set.of("CLIENTE","PROVEEDOR","ASISTENTE_ADMINISTRATIVA").contains(role));
    }
    @Transactional
    public Resource read(Actor actor, UUID id) {
        if (!actor.manager() && !(operational(actor) && identity.member(actor.id(),id)) && !support.authorizeRead(actor,id)) throw ApiException.missing();
        return load(id,false);
    }
    public Page list(Actor actor, int page, int size) {
        if (page<0 || page>100000 || size<1 || size>100) throw new ApiException(400,"INVALID_PAGINATION","Paginación inválida.");
        List<Resource> items;
        var mapper = (org.springframework.jdbc.core.RowMapper<Resource>) (rs,n) -> new Resource(rs.getObject("id",UUID.class),rs.getString("name"),rs.getLong("version"));
        if (actor.manager()) items = jdbc.query("SELECT id,name,version FROM foundation_resource ORDER BY id LIMIT ? OFFSET ?",mapper,size,page*size);
        else if (operational(actor)) items = jdbc.query("SELECT r.id,r.name,r.version FROM foundation_resource r JOIN resource_membership m ON m.resource_id=r.id WHERE m.user_id=? AND m.revoked_at IS NULL ORDER BY r.id LIMIT ? OFFSET ?",mapper,actor.id(),size,page*size);
        else items = List.of(); // Soporte no permite enumerar los recursos de la empresa.
        return new Page(items,page,size);
    }
    @Transactional
    public Resource create(Actor actor, String key, String name) {
        actor.requireManager();
        return idempotency.execute(actor.id(),"POST /foundation/resources",key,name,Resource.class,() -> {
            UUID id=UUID.randomUUID();
            jdbc.update("INSERT INTO foundation_resource(id,name,created_by) VALUES (?,?,?)",id,name,actor.id());
            audit.record(actor.id(),"FOUNDATION_CREATED",id); return load(id,false);
        });
    }
    @Transactional
    public Resource rename(Actor actor, UUID id, String key, String expectedVersion, String name) {
        actor.requireManager();
        if (expectedVersion == null) throw new ApiException(428,"PRECONDITION_REQUIRED","Incluya If-Match.");
        if (!expectedVersion.matches("\"[1-9][0-9]*\"")) throw new ApiException(400,"MALFORMED_REQUEST","ETag inválido.");
        return idempotency.execute(actor.id(),"PATCH /foundation/resources/"+id,key,new Command(name,expectedVersion),Resource.class,() -> {
            Resource current=load(id,true);
            if (!expectedVersion.equals("\""+current.version()+"\"")) throw new ApiException(412,"VERSION_MISMATCH","El recurso cambió. Actualice antes de guardar.");
            jdbc.update("UPDATE foundation_resource SET name=?,version=version+1 WHERE id=?",name,id);
            audit.record(actor.id(),"FOUNDATION_RENAMED",id); return load(id,false);
        });
    }
    @Transactional
    public Queued enqueue(Actor actor, UUID id, String key) {
        actor.requireManager(); load(id,true);
        return idempotency.execute(actor.id(),"POST /foundation/resources/"+id+"/receipts",key,id,Queued.class,
                () -> new Queued(jobs.enqueue(actor,id),"PENDING"));
    }
}
