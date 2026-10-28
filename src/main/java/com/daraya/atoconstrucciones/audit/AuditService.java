package com.daraya.atoconstrucciones.audit;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

@Service
public class AuditService {
    private final JdbcTemplate jdbc;
    public AuditService(JdbcTemplate jdbc) { this.jdbc = jdbc; }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, UUID resourceId) {
        jdbc.update("INSERT INTO audit_event(id, actor_id, action, resource_id, trace_id) VALUES (?, ?, ?, ?, ?)",
                UUID.randomUUID(), actorId, action, resourceId, UUID.randomUUID().toString());
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public void record(UUID actorId, String action, UUID resourceId, java.util.Map<String, Object> metadata) {
        var json = tools.jackson.databind.json.JsonMapper.builder().build();
        jdbc.update("INSERT INTO audit_event(id, actor_id, action, resource_id, trace_id, metadata) VALUES (?, ?, ?, ?, ?, ?::jsonb)",
                UUID.randomUUID(), actorId, action, resourceId, UUID.randomUUID().toString(), json.writeValueAsString(metadata));
    }
}
