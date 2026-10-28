package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;

@Component
public class KeycloakAccountProvisioning implements AccountProvisioningPort {
    private final JsonMapper json;
    private final String issuer,clientId,secret,webClient,webOrigin;
    private final boolean enabled;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build();
    public KeycloakAccountProvisioning(JsonMapper json,
            @Value("${spring.security.oauth2.client.provider.ato.issuer-uri}") String issuer,
            @Value("${ato.accounts.provisioning-enabled:false}") boolean enabled,
            @Value("${ato.accounts.client-id:ato-accounts}") String clientId,
            @Value("${ato.accounts.client-secret:}") String secret,
            @Value("${spring.security.oauth2.client.registration.ato.client-id}") String webClient,
            @Value("${ato.web-origin}") String webOrigin) {
        this.json=json;this.issuer=issuer;this.enabled=enabled;this.clientId=clientId;this.secret=secret;this.webClient=webClient;this.webOrigin=webOrigin;
    }
    public boolean configured(){return enabled&&!secret.isBlank()&&!clientId.isBlank();}
    private ApiException unavailable(){return new ApiException(502,"IDENTITY_PROVIDER_UNAVAILABLE","No se pudo completar la operación con el servicio de cuentas. Puedes reintentar.");}
    private URI base(){
        if(!configured())throw new ApiException(503,"ACCOUNT_PROVISIONING_UNAVAILABLE","El alta de cuentas todavía no está configurada.");
        URI uri=URI.create(issuer);
        if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||
            !uri.getPath().matches("(?:/[A-Za-z0-9_-]+)*/realms/[A-Za-z0-9_-]+")||
            !(uri.getScheme().equals("https")||uri.getScheme().equals("http")&&Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost())))throw unavailable();
        return uri;
    }
    private String admin(){String value=base().toString();int index=value.lastIndexOf("/realms/");return value.substring(0,index)+"/admin"+value.substring(index);}
    private record Reply(int status,String body) {}
    private HttpResponse.BodySubscriber<String> boundedBody(){
        var delegate=HttpResponse.BodySubscribers.ofString(StandardCharsets.UTF_8);
        return new HttpResponse.BodySubscriber<>(){
            private Flow.Subscription subscription;private int size;
            public CompletionStage<String> getBody(){return delegate.getBody();}
            public void onSubscribe(Flow.Subscription value){subscription=value;delegate.onSubscribe(value);}
            public void onNext(List<ByteBuffer> buffers){for(var buffer:buffers){size+=buffer.remaining();if(size>1_048_576){subscription.cancel();delegate.onError(new IllegalStateException("Response limit"));return;}}delegate.onNext(buffers);}
            public void onError(Throwable error){delegate.onError(error);}
            public void onComplete(){delegate.onComplete();}
        };
    }
    private Reply request(String url,String method,String body,String token,boolean form){
        try{
            var builder=HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(10)).header("Accept","application/json");
            if(token!=null)builder.header("Authorization","Bearer "+token);
            if(body!=null)builder.header("Content-Type",form?"application/x-www-form-urlencoded":"application/json");
            var pending=http.sendAsync(builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body)).build(),info->boundedBody());
            try{var response=pending.get(10,TimeUnit.SECONDS);return new Reply(response.statusCode(),response.body());}
            catch(TimeoutException e){pending.cancel(true);throw unavailable();}
        }catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}
        catch(ApiException e){throw e;}catch(Exception e){throw unavailable();}
    }
    private static String encode(String value){return URLEncoder.encode(value,StandardCharsets.UTF_8);}
    private String token(){
        var reply=request(base()+"/protocol/openid-connect/token","POST","grant_type=client_credentials&client_id="+encode(clientId)+"&client_secret="+encode(secret),null,true);
        if(reply.status()!=200)throw unavailable();
        String token=json.readTree(reply.body()).path("access_token").asString("");if(token.isBlank())throw unavailable();return token;
    }
    private JsonNode find(String reference,String token){
        var reply=request(admin()+"/users?q="+encode("atoProvisioningId:"+reference)+"&max=2","GET",null,token,false);
        if(reply.status()!=200)throw unavailable();var users=json.readTree(reply.body());
        if(!users.isArray()||users.size()>1)throw unavailable();return users.isEmpty()?null:users.path(0);
    }
    public Provisioned create(String reference,String fingerprint,String name,String email){
        String username=email,token=token();JsonNode user=find(reference,token);
        if(user==null){
            String[] parts=name.split("\\s+",2);
            var input=Map.of("username",username,"email",email,"firstName",parts[0],"lastName",parts.length>1?parts[1]:"","enabled",true,"emailVerified",false,
                "requiredActions",List.of("VERIFY_EMAIL","UPDATE_PASSWORD"),
                "attributes",Map.of("atoProvisioningId",List.of(reference),"atoProvisioningHash",List.of(fingerprint)));
            var reply=request(admin()+"/users","POST",json.writeValueAsString(input),token,false);
            if(reply.status()!=201&&reply.status()!=409)throw unavailable();
            user=find(reference,token);
            if(user==null&&reply.status()==409)throw new ApiException(409,"ACCOUNT_ALREADY_EXISTS","Ya existe una cuenta con ese correo. Pídele ingresar y revisa su cuenta en Usuarios y accesos.");
        }
        if(user==null||!user.path("username").asString().equals(username)||!user.path("email").asString().equalsIgnoreCase(email)||
            !user.path("attributes").path("atoProvisioningId").path(0).asString().equals(reference)||
            !user.path("attributes").path("atoProvisioningHash").path(0).asString().equals(fingerprint)||!user.path("enabled").asBoolean())throw new ApiException(409,"PROVISIONING_CONFLICT","La cuenta requiere revisión antes de continuar el alta.");
        String subject=user.path("id").asString();if(subject.isBlank()||subject.contains("/"))throw unavailable();
        return new Provisioned(issuer,subject);
    }
    public void sendActivation(String targetIssuer,String subject){
        if(!issuer.equals(targetIssuer)||!subject.matches("[A-Za-z0-9_-]{1,100}"))throw unavailable();
        String token=token();
        var user=request(admin()+"/users/"+encode(subject),"GET",null,token,false);
        if(user.status()!=200||json.readTree(user.body()).path("attributes").path("atoProvisioningId").path(0).asString("").isBlank())throw unavailable();
        var reply=request(admin()+"/users/"+encode(subject)+"/execute-actions-email?client_id="+encode(webClient)+"&redirect_uri="+encode(webOrigin+"/login")+"&lifespan=86400","PUT",json.writeValueAsString(List.of("VERIFY_EMAIL","UPDATE_PASSWORD")),token,false);
        if(reply.status()!=204)throw unavailable();
    }
}
