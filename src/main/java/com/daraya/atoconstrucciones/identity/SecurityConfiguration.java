package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.shared.ApiErrors;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.SecurityContextHolderFilter;
import org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository;
import org.springframework.web.filter.OncePerRequestFilter;
import tools.jackson.databind.json.JsonMapper;
import java.io.IOException;
import java.util.Arrays;

@Configuration
public class SecurityConfiguration {
    private final JsonMapper json;
    private final IdentityService identity;
    public SecurityConfiguration(JsonMapper json, IdentityService identity) { this.json = json; this.identity = identity; }
    private void error(HttpServletRequest request, HttpServletResponse response, int status, String code) throws IOException {
        response.setStatus(status); response.setContentType("application/problem+json");
        response.getWriter().write(json.writeValueAsString(ApiErrors.problem(status, code, "Solicitud no autorizada.", request.getRequestURI())));
    }
    @Bean @Order(1)
    SecurityFilterChain bearer(HttpSecurity http) throws Exception {
        http.securityMatcher(request -> {
            String header = request.getHeader("Authorization");
            return header != null && header.regionMatches(true, 0, "Bearer ", 0, 7);
        });
        http.sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        http.csrf(csrf -> csrf.disable());
        // Una sesión web nunca puede eludir CSRF añadiendo un bearer.
        http.addFilterBefore(new OncePerRequestFilter() {
            @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain) throws ServletException, IOException {
                if (request.getCookies() != null && Arrays.stream(request.getCookies()).anyMatch(cookie -> cookie.getName().equals("ATOSESSION"))) {
                    error(request, response, 400, "MIXED_AUTHENTICATION"); return;
                }
                chain.doFilter(request, response);
            }
        }, SecurityContextHolderFilter.class);
        http.authorizeHttpRequests(auth -> auth.requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/public/**").permitAll().requestMatchers("/api/v1/**").authenticated().anyRequest().denyAll());
        http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> {}).authenticationEntryPoint((request,response,exception) -> error(request,response,401,"UNAUTHENTICATED")));
        http.exceptionHandling(errors -> errors.accessDeniedHandler((request,response,exception) -> error(request,response,403,"FORBIDDEN")));
        return http.build();
    }
    @Bean @Order(2)
    SecurityFilterChain web(HttpSecurity http, ClientRegistrationRepository clients, @Value("${ato.web-origin}") String webOrigin) throws Exception {
        var resolver = new DefaultOAuth2AuthorizationRequestResolver(clients, "/oauth2/authorization");
        resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce().andThen(builder ->
            builder.additionalParameters(parameters -> {parameters.put("prompt","login");parameters.put("max_age","0");parameters.put("ui_locales","es");})));
        var registrationResolver=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
        registrationResolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce().andThen(builder ->
            builder.additionalParameters(parameters -> {parameters.put("prompt","create");parameters.put("ui_locales","es");})));
        var requests=new org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver(){
            private org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver delegate(HttpServletRequest request){return "true".equals(request.getParameter("registration"))?registrationResolver:resolver;}
            public org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest resolve(HttpServletRequest request){return delegate(request).resolve(request);}
            public org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest resolve(HttpServletRequest request,String id){return delegate(request).resolve(request,id);}
        };
        var users = new OidcUserService();
        http.authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/health/**", "/auth/login", "/auth/register", "/api/v1/session/csrf", "/oauth2/**", "/login/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.GET,"/api/v1/public/**").permitAll()
                .requestMatchers(org.springframework.http.HttpMethod.POST,"/api/v1/public/contact").permitAll()
                .requestMatchers("/api/v1/**", "/auth/logout").authenticated().anyRequest().denyAll());
        http.csrf(csrf -> csrf.csrfTokenRepository(new HttpSessionCsrfTokenRepository()).ignoringRequestMatchers("/api/v1/public/contact"));
        http.oauth2Login(oauth -> oauth.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(requests))
                .userInfoEndpoint(endpoint -> endpoint.oidcUserService(request -> {
                    var user = users.loadUser(request);
                    identity.register(user.getIssuer().toString(), user.getSubject(), user.getEmail(), Boolean.TRUE.equals(user.getEmailVerified()),user.getFullName());
                    return user;
                }))
                .successHandler((request,response,authentication) -> response.sendRedirect(webOrigin + "/fundamentos"))
                .failureHandler((request,response,exception) -> response.sendRedirect(webOrigin + "/?authError=1")));
        http.logout(logout -> logout.logoutUrl("/auth/logout").invalidateHttpSession(true).clearAuthentication(true)
                .deleteCookies("ATOSESSION").logoutSuccessHandler((request,response,auth) -> response.setStatus(204)));
        http.exceptionHandling(errors -> errors
                .authenticationEntryPoint((request,response,exception) -> error(request,response,401,"UNAUTHENTICATED"))
                .accessDeniedHandler((request,response,exception) -> error(request,response,403,"FORBIDDEN")));
        return http.build();
    }
}
