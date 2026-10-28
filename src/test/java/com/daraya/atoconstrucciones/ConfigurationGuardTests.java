package com.daraya.atoconstrucciones;

import com.daraya.atoconstrucciones.shared.ConfigurationGuard;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.assertj.core.api.Assertions.*;

class ConfigurationGuardTests {
    MockEnvironment valid() {
        return new MockEnvironment().withProperty("ATO_DB_PASSWORD","x".repeat(32))
                .withProperty("ATO_OIDC_CLIENT_SECRET","y".repeat(32))
                .withProperty("ato.contact.rate-secret","z".repeat(32))
                .withProperty("ato.web-origin","https://ato.example.invalid")
                .withProperty("spring.security.oauth2.client.provider.ato.issuer-uri","https://idp.example.invalid/realms/ato");
    }
    @Test void productionRequiresSecretsHttpsAndSecureCookies() {
        assertThatThrownBy(() -> new ConfigurationGuard(new MockEnvironment()).afterSingletonsInstantiated()).isInstanceOf(IllegalStateException.class);
        assertThatCode(() -> new ConfigurationGuard(valid()).afterSingletonsInstantiated()).doesNotThrowAnyException();
        assertThatThrownBy(() -> new ConfigurationGuard(valid().withProperty("ato.web-origin","http://localhost:3000")).afterSingletonsInstantiated()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ConfigurationGuard(valid().withProperty("server.servlet.session.cookie.secure","false")).afterSingletonsInstantiated()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ConfigurationGuard(valid().withProperty("ato.bootstrap-manager-email","gerente@example.invalid")).afterSingletonsInstantiated()).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> new ConfigurationGuard(valid().withProperty("debug","true")).afterSingletonsInstantiated()).isInstanceOf(IllegalStateException.class);
    }
}
