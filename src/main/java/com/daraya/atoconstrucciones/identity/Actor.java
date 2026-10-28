package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.shared.ApiException;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;

public record Actor(UUID id, String email, Set<String> roles, boolean mfa, Instant authenticatedAt) {
    public boolean manager() { return roles.contains("GERENTE"); }
    public void requireManager() { if (!manager()) throw ApiException.forbidden(); }
    public void requireRecentAuthentication() {
        if (authenticatedAt == null || authenticatedAt.isBefore(Instant.now().minusSeconds(300)) || authenticatedAt.isAfter(Instant.now().plusSeconds(60))) {
            throw new ApiException(403, "REAUTHENTICATION_REQUIRED", "Reautentíquese para esta acción.");
        }
    }
}
