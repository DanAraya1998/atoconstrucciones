package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class SupportService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final SensitiveAuthenticationPolicy sensitive;
    public SupportService(JdbcTemplate jdbc, AuditService audit, SensitiveAuthenticationPolicy sensitive) { this.jdbc = jdbc; this.audit = audit; this.sensitive = sensitive; }
    @Transactional
    public UUID request(Actor actor, UUID resourceId, String purpose) {
        if (!actor.roles().contains("ADMIN")) throw ApiException.forbidden();
        sensitive.require(actor);
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO support_request(id,user_id,resource_id,purpose) VALUES (?,?,?,?)", id, actor.id(), resourceId, purpose);
        audit.record(actor.id(), "SUPPORT_REQUESTED", resourceId); return id;
    }
    @Transactional
    public void approve(Actor actor, UUID id) {
        actor.requireManager(); sensitive.require(actor);
        var rows = jdbc.queryForList("SELECT user_id,resource_id,approved_by,revoked_at FROM support_request WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw ApiException.missing();
        var row = rows.getFirst();
        if (actor.id().equals(row.get("user_id")) || row.get("approved_by") != null || row.get("revoked_at") != null) throw ApiException.forbidden();
        jdbc.update("UPDATE support_request SET approved_by=?,expires_at=now()+interval '30 minutes' WHERE id=?", actor.id(), id);
        audit.record(actor.id(), "SUPPORT_APPROVED", (UUID) row.get("resource_id"));
    }
    @Transactional
    public void revoke(Actor actor, UUID id) {
        actor.requireManager();
        var rows = jdbc.queryForList("SELECT resource_id FROM support_request WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw ApiException.missing();
        jdbc.update("UPDATE support_request SET revoked_at=now() WHERE id=?", id);
        audit.record(actor.id(), "SUPPORT_REVOKED", (UUID) rows.getFirst().get("resource_id"));
    }
    @Transactional
    public boolean authorizeRead(Actor actor, UUID resourceId) {
        if (!actor.roles().contains("ADMIN")) return false;
        sensitive.require(actor);
        // Lock de lectura: la revocación se serializa con el acceso autorizado.
        var grants = jdbc.queryForList("SELECT id FROM support_request WHERE user_id=? AND resource_id=? AND approved_by IS NOT NULL AND revoked_at IS NULL AND expires_at>now() FOR SHARE",
                actor.id(), resourceId);
        if (grants.isEmpty()) return false;
        audit.record(actor.id(), "SUPPORT_RESOURCE_READ", resourceId); return true;
    }
}
