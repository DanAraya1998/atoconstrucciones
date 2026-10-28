package com.daraya.atoconstrucciones.shared;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class IdempotencyService {
    private final JdbcTemplate jdbc;
    private final JsonMapper json;
    public IdempotencyService(JdbcTemplate jdbc, JsonMapper json) { this.jdbc = jdbc; this.json = json; }
    public static String hash(String value) {
        try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); }
        catch (NoSuchAlgorithmException e) { throw new IllegalStateException(e); }
    }
    @Transactional(propagation = Propagation.MANDATORY)
    public <T> T execute(UUID actorId, String operation, String key, Object input, Class<T> type, Supplier<T> action) {
        if (key == null || !key.matches("[A-Za-z0-9._:-]{8,128}")) {
            throw new ApiException(400, "IDEMPOTENCY_KEY_REQUIRED", "Incluya una clave de idempotencia válida.");
        }
        String fingerprint = hash(json.writeValueAsString(input));
        jdbc.update("INSERT INTO idempotency_record(actor_id, operation, key, request_hash) VALUES (?, ?, ?, ?) ON CONFLICT DO NOTHING",
                actorId, operation, key, fingerprint);
        var row = jdbc.queryForMap("SELECT request_hash, response::text AS response FROM idempotency_record WHERE actor_id=? AND operation=? AND key=? FOR UPDATE",
                actorId, operation, key);
        if (!fingerprint.equals(row.get("request_hash"))) {
            throw new ApiException(409, "IDEMPOTENCY_CONFLICT", "La clave ya corresponde a otra solicitud.");
        }
        if (row.get("response") != null) return json.readValue(row.get("response").toString(), type);
        T response = action.get();
        jdbc.update("UPDATE idempotency_record SET response=?::jsonb WHERE actor_id=? AND operation=? AND key=?",
                json.writeValueAsString(response), actorId, operation, key);
        return response;
    }
}
