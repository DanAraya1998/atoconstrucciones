package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.shared.ApiException;
import com.sun.net.httpserver.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.assertj.core.api.Assertions.*;

class KeycloakAccountProvisioningTests {
    HttpServer server;String issuer;JsonMapper json=JsonMapper.builder().build();Map<String,Object> user;AtomicInteger creates,deliveries;boolean duplicate,failEmail;
    @BeforeEach void start()throws Exception{
        creates=new AtomicInteger();deliveries=new AtomicInteger();server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);issuer="http://127.0.0.1:"+server.getAddress().getPort()+"/realms/ato";
        server.createContext("/",exchange->{
            String path=exchange.getRequestURI().getPath(),method=exchange.getRequestMethod();int status=200;Object result=Map.of();
            if(path.endsWith("/token")){assertThat(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)).contains("grant_type=client_credentials");result=Map.of("access_token","synthetic-token");}
            else{
                assertThat(exchange.getRequestHeaders().getFirst("Authorization")).isEqualTo("Bearer synthetic-token");
                if(path.endsWith("/users")&&method.equals("GET")){assertThat(exchange.getRequestURI().getRawQuery()).doesNotContain("example.invalid");result=user==null?List.of():List.of(user);}
                else if(path.endsWith("/users")&&method.equals("POST")){
                    creates.incrementAndGet();if(duplicate)status=409;else{
                        @SuppressWarnings("unchecked") Map<String,Object> input=json.readValue(exchange.getRequestBody().readAllBytes(),Map.class);
                        assertThat(input).doesNotContainKeys("credentials","realmRoles","clientRoles");assertThat(input.get("emailVerified")).isEqualTo(false);assertThat(input.get("requiredActions")).isEqualTo(List.of("VERIFY_EMAIL","UPDATE_PASSWORD"));
                        user=new HashMap<>(input);user.put("id","synthetic-user");status=201;
                    }
                }else if(path.endsWith("/execute-actions-email")){deliveries.incrementAndGet();assertThat(method).isEqualTo("PUT");status=failEmail?503:204;}
                else if(path.endsWith("/users/synthetic-user"))result=user;
                else status=404;
            }
            byte[] body=json.writeValueAsBytes(result);exchange.getResponseHeaders().set("Content-Type","application/json");exchange.sendResponseHeaders(status,status==204?-1:body.length);if(status!=204)exchange.getResponseBody().write(body);exchange.close();
        });server.start();
    }
    @AfterEach void stop(){server.stop(0);}
    KeycloakAccountProvisioning adapter(){return new KeycloakAccountProvisioning(json,issuer,true,"ato-accounts","synthetic-secret","ato-web","http://localhost:3302");}
    @Test void createsWithNoPasswordAndReconcilesRemoteSuccessAfterLocalRollback(){
        var port=adapter();var account=port.create("reference-1","fingerprint-1","Persona demo","person@example.invalid");assertThat(port.create("reference-1","fingerprint-1","Persona demo","person@example.invalid")).isEqualTo(account);assertThat(creates.get()).isEqualTo(1);port.sendActivation(account.issuer(),account.subject());assertThat(deliveries.get()).isEqualTo(1);
    }
    @Test void emailCollisionIsNeverAdoptedAndChangedRetryCannotTakeOverIdentity(){
        duplicate=true;assertThatThrownBy(()->adapter().create("reference-1","hash","Persona demo","existing@example.invalid")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("ACCOUNT_ALREADY_EXISTS"));
        duplicate=false;adapter().create("reference-1","hash","Persona demo","new@example.invalid");assertThatThrownBy(()->adapter().create("reference-1","other-hash","Persona demo","new@example.invalid")).isInstanceOfSatisfying(ApiException.class,e->assertThat(e.code()).isEqualTo("PROVISIONING_CONFLICT"));
    }
    @Test void onlyAtoProvisionedAccountsCanReceiveActivationAndTransportFailureIsSanitized(){
        var account=adapter().create("reference-1","hash","Persona demo","person@example.invalid");user.put("attributes",Map.of());assertThatThrownBy(()->adapter().sendActivation(account.issuer(),account.subject())).isInstanceOf(ApiException.class);assertThat(deliveries.get()).isZero();
        user.put("attributes",Map.of("atoProvisioningId",List.of("reference-1")));failEmail=true;assertThatThrownBy(()->adapter().sendActivation(account.issuer(),account.subject())).isInstanceOfSatisfying(ApiException.class,e->{assertThat(e.status()).isEqualTo(502);assertThat(e.getMessage()).doesNotContain("synthetic-token","synthetic-secret","person@example.invalid");});
        assertThatThrownBy(()->adapter().sendActivation("https://other.example.invalid","synthetic-user")).isInstanceOf(ApiException.class);
    }
}
