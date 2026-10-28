package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.shared.ApiException;
import com.daraya.atoconstrucciones.shared.IdempotencyService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.*;

@Service
public class InvitationService {
    public record Invitation(UUID id, String token, Instant expiresAt) {}
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final SensitiveAuthenticationPolicy sensitive;
    public InvitationService(JdbcTemplate jdbc, AuditService audit, SensitiveAuthenticationPolicy sensitive) { this.jdbc = jdbc; this.audit = audit; this.sensitive = sensitive; }
    @Transactional
    public Invitation create(Actor actor, String email, String role) {
        actor.requireManager(); sensitive.require(actor);
        if (!Set.of("ADMIN","ASISTENTE_ADMINISTRATIVA","PROVEEDOR","CLIENTE").contains(role)) throw ApiException.forbidden();
        byte[] bytes = new byte[32]; new SecureRandom().nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        UUID id = UUID.randomUUID(); Instant expires = Instant.now().plusSeconds(86400);
        jdbc.update("INSERT INTO invitation(id,email,role,token_hash,expires_at,created_by) VALUES (?,?,?,?,?,?)",
                id, email.toLowerCase(Locale.ROOT), role, IdempotencyService.hash(token), java.sql.Timestamp.from(expires), actor.id());
        audit.record(actor.id(), "INVITATION_CREATED", id);
        return new Invitation(id, token, expires);
    }
    @Transactional
    public void accept(Actor actor, UUID id, String token) {
        var state=jdbc.queryForMap("SELECT enabled,access_state FROM user_account WHERE id=? FOR UPDATE",actor.id());
        if(!Boolean.TRUE.equals(state.get("enabled"))||"REJECTED".equals(state.get("access_state")))throw ApiException.forbidden();
        var rows = jdbc.queryForList("SELECT email,role,token_hash,expires_at,accepted_at FROM invitation WHERE id=? FOR UPDATE", id);
        if (rows.isEmpty()) throw ApiException.missing();
        var row = rows.getFirst();
        if (!actor.email().equalsIgnoreCase(row.get("email").toString()) ||
                !IdempotencyService.hash(token).equals(row.get("token_hash")) ||
                ((java.sql.Timestamp) row.get("expires_at")).toInstant().isBefore(Instant.now())) throw ApiException.missing();
        if (row.get("accepted_at") != null) throw new ApiException(409, "INVITATION_USED", "La invitación ya fue utilizada.");
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?) ON CONFLICT DO NOTHING", actor.id(), row.get("role"));
        jdbc.update("UPDATE user_account SET access_state='APPROVED',access_version=access_version+1 WHERE id=?",actor.id());
        jdbc.update("UPDATE invitation SET accepted_by=?,accepted_at=now() WHERE id=?", actor.id(), id);
        audit.record(actor.id(), "INVITATION_ACCEPTED", id);
    }
}
