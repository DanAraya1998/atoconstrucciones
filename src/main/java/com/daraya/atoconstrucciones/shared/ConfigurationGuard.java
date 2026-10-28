package com.daraya.atoconstrucciones.shared;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Arrays;

@Component
public class ConfigurationGuard implements SmartInitializingSingleton {
    private final Environment environment;
    public ConfigurationGuard(Environment environment) { this.environment = environment; }
    @Override public void afterSingletonsInstantiated() {
        if (Arrays.asList(environment.getActiveProfiles()).contains("dev") || Arrays.asList(environment.getActiveProfiles()).contains("test")) return;
        for (String property : new String[]{"ATO_DB_PASSWORD", "ATO_OIDC_CLIENT_SECRET", "ato.contact.rate-secret"}) {
            String value = environment.getProperty(property);
            if (value == null || value.length() < 24) throw new IllegalStateException("Configuración requerida inválida: " + property);
        }
        for (String property : new String[]{"ato.web-origin", "spring.security.oauth2.client.provider.ato.issuer-uri"}) {
            URI uri = URI.create(environment.getRequiredProperty(property));
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null) throw new IllegalStateException("HTTPS requerido: " + property);
        }
        if(environment.getProperty("ato.mail.enabled",Boolean.class,false)) {
            for(String property:new String[]{"ato.mail.username","ato.mail.recipient"}) {
                String value=environment.getProperty(property,"");
                if(!value.matches("[^\\s@]+@[^\\s@]+\\.[^\\s@]+")||value.endsWith(".invalid"))throw new IllegalStateException("Correo real requerido: "+property);
            }
            if(environment.getProperty("ato.mail.password","").isBlank()||!environment.getProperty("ato.mail.sender-confirmed",Boolean.class,false))throw new IllegalStateException("Gmail requiere credencial y remitente confirmado");
        }
        if(environment.getProperty("ato.contact.trusted-tunnel",Boolean.class,false)&&!"127.0.0.1".equals(environment.getProperty("server.address","")))throw new IllegalStateException("El origen del túnel debe escuchar en loopback");
        if (!environment.getProperty("server.servlet.session.cookie.secure", Boolean.class, true) ||
                environment.getProperty("debug", Boolean.class, false) ||
                !environment.getProperty("ato.bootstrap-manager-email", "").isBlank()) throw new IllegalStateException("Configuración de producción insegura");
    }
}
