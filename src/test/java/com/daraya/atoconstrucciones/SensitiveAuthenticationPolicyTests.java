package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.identity.Actor;
import com.daraya.atoconstrucciones.identity.SensitiveAuthenticationPolicy;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class SensitiveAuthenticationPolicyTests {
    @Test void mfaIsOptionalButRecentAuthenticationIsRequired() {
        var actor=new Actor(UUID.randomUUID(),"synthetic@example.invalid",Set.of("GERENTE"),false,Instant.now());
        assertThatCode(() -> new SensitiveAuthenticationPolicy(false).require(actor)).doesNotThrowAnyException();
        assertThatThrownBy(() -> new SensitiveAuthenticationPolicy(true).require(actor)).hasMessageContaining("MFA");
        var stale=new Actor(actor.id(),actor.email(),actor.roles(),false,Instant.now().minusSeconds(301));
        assertThatThrownBy(() -> new SensitiveAuthenticationPolicy(false).require(stale)).hasMessageContaining("Reautentíquese");
    }
}
