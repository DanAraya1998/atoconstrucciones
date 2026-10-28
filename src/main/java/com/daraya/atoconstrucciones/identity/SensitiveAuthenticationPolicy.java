package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class SensitiveAuthenticationPolicy {
    private final boolean requireMfa;
    public SensitiveAuthenticationPolicy(@Value("${ato.security.require-mfa:false}") boolean requireMfa) { this.requireMfa=requireMfa; }
    public void require(Actor actor) {
        actor.requireRecentAuthentication();
        if (requireMfa && !actor.mfa()) throw new ApiException(403,"MFA_REQUIRED","Complete MFA para esta acción.");
    }
}
