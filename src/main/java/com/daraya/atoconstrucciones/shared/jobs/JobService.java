package com.daraya.atoconstrucciones.shared.jobs;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.identity.IdentityService;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.UUID;

@Service
public class JobService {
    public record Lease(UUID id, UUID token, UUID actorId, UUID resourceId, String kind) {}
    public record Job(UUID id, UUID resourceId, String state, int attempts, String errorCode) {}
    private final JdbcTemplate jdbc;
    private final IdentityService identity;
    private final AuditService audit;
    private final int maxAttempts;
    private final int leaseSeconds;
    public JobService(JdbcTemplate jdbc, IdentityService identity, AuditService audit,
                      @Value("${ato.jobs.max-attempts:5}") int maxAttempts, @Value("${ato.jobs.lease-seconds:60}") int leaseSeconds) {
        this.jdbc = jdbc; this.identity = identity; this.audit = audit; this.maxAttempts = maxAttempts; this.leaseSeconds = leaseSeconds;
    }
    @Transactional
    public UUID enqueue(Actor actor, UUID resourceId) {
        actor.requireManager(); UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO outbox_job(id,actor_id,resource_id,kind,max_attempts) VALUES (?,?,?,'FOUNDATION_RECEIPT',?)", id, actor.id(), resourceId, maxAttempts);
        audit.record(actor.id(), "JOB_ENQUEUED", resourceId); return id;
    }
    @Transactional
    public List<Lease> claim() {
        jdbc.update("UPDATE outbox_job SET state='DEAD',error_code='LEASE_EXHAUSTED',lease_token=NULL,lease_until=NULL WHERE state='RUNNING' AND lease_until<now() AND attempts>=max_attempts");
        UUID token = UUID.randomUUID();
        return jdbc.query("""
                WITH candidate AS (
                    SELECT id FROM outbox_job WHERE attempts<max_attempts AND
                    ((state='PENDING' AND available_at<=now()) OR (state='RUNNING' AND lease_until<now()))
                    ORDER BY created_at FOR UPDATE SKIP LOCKED LIMIT 1
                ) UPDATE outbox_job j SET state='RUNNING',attempts=attempts+1,lease_token=?,
                    lease_until=now()+make_interval(secs => ?) FROM candidate c WHERE j.id=c.id
                    RETURNING j.id,j.actor_id,j.resource_id,j.kind
                """, (rs,n) -> new Lease(rs.getObject("id",UUID.class), token, rs.getObject("actor_id",UUID.class), rs.getObject("resource_id",UUID.class), rs.getString("kind")), token, leaseSeconds);
    }
    @Transactional
    public void complete(Lease lease) {
        var rows = jdbc.queryForList("SELECT id FROM outbox_job WHERE id=? AND lease_token=? AND state='RUNNING' AND lease_until>now() FOR UPDATE", lease.id(), lease.token());
        if (rows.isEmpty()) return; // Un worker antiguo no confirma el lease recuperado por otro.
        if (!"FOUNDATION_RECEIPT".equals(lease.kind())) throw new IllegalStateException("UNSUPPORTED_JOB");
        if (jdbc.queryForObject("SELECT count(*) FROM user_account a JOIN user_role r ON a.id=r.user_id WHERE a.id=? AND a.enabled AND r.role='GERENTE'", Long.class, lease.actorId()) == 0) throw new IllegalStateException("AUTHORIZATION_REVOKED");
        jdbc.update("INSERT INTO foundation_receipt(job_id,resource_id) VALUES (?,?) ON CONFLICT DO NOTHING", lease.id(), lease.resourceId());
        jdbc.update("UPDATE outbox_job SET state='SUCCEEDED',completed_at=now(),lease_token=NULL,lease_until=NULL,error_code=NULL WHERE id=?", lease.id());
        audit.record(lease.actorId(), "JOB_COMPLETED", lease.resourceId());
    }
    @Transactional
    public void fail(Lease lease, String code) {
        jdbc.update("""
                UPDATE outbox_job SET state=CASE WHEN attempts>=max_attempts THEN 'DEAD' ELSE 'PENDING' END,
                available_at=now()+make_interval(secs => LEAST(300,power(2,attempts)::integer)+floor(random()*3)::integer),
                lease_token=NULL,lease_until=NULL,error_code=? WHERE id=? AND lease_token=? AND state='RUNNING'
                """, code, lease.id(), lease.token());
    }
    public Job read(Actor actor, UUID id) {
        var rows = jdbc.query("SELECT id,resource_id,state,attempts,error_code FROM outbox_job WHERE id=?", (rs,n) -> new Job(rs.getObject("id",UUID.class), rs.getObject("resource_id",UUID.class), rs.getString("state"), rs.getInt("attempts"), rs.getString("error_code")), id);
        if (rows.isEmpty()) throw ApiException.missing();
        Job job = rows.getFirst();
        if (!actor.manager() && (!identity.member(actor.id(), job.resourceId()) || actor.roles().stream().noneMatch(role -> List.of("CLIENTE","PROVEEDOR","ASISTENTE_ADMINISTRATIVA").contains(role)))) throw ApiException.missing();
        return job;
    }
}
