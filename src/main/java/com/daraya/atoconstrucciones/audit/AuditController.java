package com.daraya.atoconstrucciones.audit;

import com.daraya.atoconstrucciones.identity.IdentityService;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
public class AuditController {
    public record Event(UUID id, UUID actorId, String action, UUID resourceId, Instant recordedAt) {}
    private final JdbcTemplate jdbc;
    private final IdentityService identity;
    public AuditController(JdbcTemplate jdbc, IdentityService identity) { this.jdbc=jdbc; this.identity=identity; }
    @GetMapping("/api/v1/audit/events")
    public ResponseEntity<List<Event>> events(Authentication auth, @RequestParam UUID resourceId, @RequestParam(defaultValue="50") int limit) {
        identity.actor(auth).requireManager();
        if (limit<1 || limit>100) throw new ApiException(400,"INVALID_PAGINATION","Límite inválido.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jdbc.query("SELECT id,actor_id,action,resource_id,recorded_at FROM audit_event WHERE resource_id=? ORDER BY recorded_at DESC,id LIMIT ?",
                (rs,n) -> new Event(rs.getObject("id",UUID.class),rs.getObject("actor_id",UUID.class),rs.getString("action"),rs.getObject("resource_id",UUID.class),rs.getTimestamp("recorded_at").toInstant()),resourceId,limit));
    }
}
