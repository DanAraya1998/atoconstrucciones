package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.audit.AuditService;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.*;

@Service
public class IdentityService {
    private final JdbcTemplate jdbc;
    private final AuditService audit;
    private final SensitiveAuthenticationPolicy sensitive;
    private final String bootstrapEmail;
    private final Environment environment;
    public IdentityService(JdbcTemplate jdbc, AuditService audit, Environment environment, SensitiveAuthenticationPolicy sensitive,
                           @Value("${ato.bootstrap-manager-email:}") String bootstrapEmail) {
        this.jdbc = jdbc; this.audit = audit; this.sensitive = sensitive; this.environment = environment; this.bootstrapEmail = bootstrapEmail;
    }
    @Transactional
    public UUID register(String issuer, String subject, String email, boolean verified) {
        return register(issuer,subject,email,verified,null);
    }
    @Transactional
    public UUID register(String issuer,String subject,String email,boolean verified,String displayName) {
        if (!verified || email == null || email.isBlank()) throw new ApiException(403, "VERIFIED_EMAIL_REQUIRED", "Se requiere correo verificado.");
        String name=displayName==null?"":displayName.replaceAll("[\\p{Cntrl}]"," ").strip();if(name.length()>200)name=name.substring(0,200);
        UUID candidate = UUID.randomUUID();
        jdbc.update("INSERT INTO user_account(id,issuer,subject,email,display_name,access_state,email_verified) VALUES (?,?,?,?,?,'PENDING',true) ON CONFLICT(issuer,subject) DO UPDATE SET access_version=user_account.access_version+CASE WHEN user_account.email<>excluded.email OR NOT user_account.email_verified OR (excluded.display_name<>'' AND user_account.display_name<>excluded.display_name) THEN 1 ELSE 0 END,email=excluded.email,email_verified=true,display_name=CASE WHEN excluded.display_name='' THEN user_account.display_name ELSE excluded.display_name END",
                candidate,issuer,subject,email.strip().toLowerCase(Locale.ROOT),name);
        UUID id = jdbc.queryForObject("SELECT id FROM user_account WHERE issuer=? AND subject=?", UUID.class, issuer, subject);
        if(candidate.equals(id))audit.record(id,"ACCOUNT_ACCESS_REQUESTED",id);
        if (Arrays.asList(environment.getActiveProfiles()).contains("dev") && email.equalsIgnoreCase(bootstrapEmail)) {
            jdbc.queryForObject("SELECT pg_advisory_xact_lock(47002001)", (rs, n) -> 1);
            if (jdbc.queryForObject("SELECT count(*) FROM user_role WHERE role='GERENTE'", Long.class) == 0) {
                jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'GERENTE')", id);
                jdbc.update("UPDATE user_account SET access_state='APPROVED' WHERE id=?",id);
                audit.record(id, "LOCAL_MANAGER_BOOTSTRAPPED", id);
            }
        }
        return id;
    }
    public Actor actor(Authentication authentication) {
        if (authentication == null || !authentication.isAuthenticated()) throw new ApiException(401, "UNAUTHENTICATED", "Inicie sesión.");
        Map<String, Object> claims;
        String issuer; String subject;
        if (authentication.getPrincipal() instanceof OidcUser user) {
            claims = user.getClaims(); issuer = user.getIssuer().toString(); subject = user.getSubject();
        } else if (authentication.getPrincipal() instanceof Jwt jwt) {
            claims = jwt.getClaims(); issuer = jwt.getIssuer().toString(); subject = jwt.getSubject();
        } else throw new ApiException(401, "UNAUTHENTICATED", "Identidad no admitida.");
        var rows = jdbc.queryForList("SELECT id,email,enabled,access_state,email_verified FROM user_account WHERE issuer=? AND subject=?", issuer, subject);
        if (rows.isEmpty() || !Boolean.TRUE.equals(rows.getFirst().get("enabled"))) {
            throw new ApiException(401, "ACCOUNT_UNAVAILABLE", "Cuenta no disponible.");
        }
        var row = rows.getFirst(); UUID id = (UUID) row.get("id");
        var roles = new HashSet<>(jdbc.queryForList("SELECT role FROM user_role WHERE user_id=?", String.class, id));
        if (!"APPROVED".equals(row.get("access_state")) || !Boolean.TRUE.equals(row.get("email_verified"))) roles.clear();
        Object amr = claims.get("amr");
        boolean mfa = amr instanceof Collection<?> values && (values.contains("otp") || values.contains("mfa") || values.contains("hwk"));
        Object authTime = claims.get("auth_time");
        Instant authenticatedAt = authTime instanceof Instant instant ? instant : authTime instanceof Number number ? Instant.ofEpochSecond(number.longValue()) : null;
        return new Actor(id, row.get("email").toString(), Set.copyOf(roles), mfa, authenticatedAt);
    }
    public boolean member(UUID actorId, UUID resourceId) {
        return jdbc.queryForObject("SELECT count(*) FROM resource_membership WHERE user_id=? AND resource_id=? AND revoked_at IS NULL", Long.class, actorId, resourceId) > 0;
    }
    @Transactional
    public void setMembership(Actor actor, UUID resourceId, UUID userId, boolean active) {
        actor.requireManager(); sensitive.require(actor);
        if (jdbc.queryForObject("SELECT count(*) FROM user_account WHERE id=? AND enabled=true", Long.class, userId) == 0) throw ApiException.missing();
        jdbc.update("INSERT INTO resource_membership(resource_id,user_id,revoked_at) VALUES (?,?,?) ON CONFLICT (resource_id,user_id) DO UPDATE SET revoked_at=excluded.revoked_at",
                resourceId, userId, active ? null : java.sql.Timestamp.from(Instant.now()));
        audit.record(actor.id(), active ? "MEMBERSHIP_GRANTED" : "MEMBERSHIP_REVOKED", resourceId);
    }
    @Transactional
    public void setRoles(Actor actor, UUID userId, Set<String> roles) {
        actor.requireManager(); sensitive.require(actor);
        Set<String> allowed = Set.of("ADMIN", "ASISTENTE_ADMINISTRATIVA", "PROVEEDOR", "CLIENTE");
        if (roles == null || !allowed.containsAll(roles)) throw ApiException.forbidden();
        var users = jdbc.queryForList("SELECT id FROM user_account WHERE id=? FOR UPDATE", userId);
        if (users.isEmpty()) throw ApiException.missing();
        if (jdbc.queryForObject("SELECT count(*) FROM user_role WHERE user_id=? AND role='GERENTE'", Long.class, userId) > 0) throw ApiException.forbidden();
        jdbc.update("DELETE FROM user_role WHERE user_id=?", userId);
        for (String role : roles) jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)", userId, role);
        jdbc.update("UPDATE user_account SET access_state=?,access_version=access_version+1 WHERE id=?",roles.isEmpty()?"PENDING":"APPROVED",userId);
        audit.record(actor.id(), "ROLES_CHANGED", userId);
    }
    @Transactional
    public void disable(Actor actor, UUID userId) {
        actor.requireManager(); sensitive.require(actor);
        var users = jdbc.queryForList("SELECT subject FROM user_account WHERE id=? FOR UPDATE", userId);
        if (users.isEmpty()) throw ApiException.missing();
        if (jdbc.queryForObject("SELECT count(*) FROM user_role WHERE user_id=? AND role='GERENTE'", Long.class, userId) > 0) throw ApiException.forbidden();
        jdbc.update("UPDATE user_account SET enabled=false,access_version=access_version+1 WHERE id=?", userId);
        jdbc.update("DELETE FROM spring_session WHERE principal_name=?", users.getFirst().get("subject"));
        audit.record(actor.id(), "ACCOUNT_DISABLED", userId);
    }
}
