package com.daraya.atoconstrucciones.documents;
import com.daraya.atoconstrucciones.identity.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import java.util.*;
@RestController @RequestMapping("/api/v1/privacy")
public class PrivacyController {
    public record Reason(@NotBlank @Size(max=2000)String reason){}
    private final PrivacyService privacy;private final IdentityService identity;
    public PrivacyController(PrivacyService privacy,IdentityService identity){this.privacy=privacy;this.identity=identity;}
    @GetMapping("/retention")public List<PrivacyService.Retention> retention(Authentication auth){identity.actor(auth);return privacy.retention();}
    @GetMapping("/notices")public List<PrivacyService.Notice> notices(Authentication auth){return privacy.notices(identity.actor(auth));}
    @PostMapping("/notices")@ResponseStatus(HttpStatus.CREATED)public PrivacyService.Notice draft(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody PrivacyService.NoticeInput input){return privacy.draft(identity.actor(auth),key,input);}
    @PostMapping("/notices/{id}/approve")public PrivacyService.Notice approve(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key){return privacy.approve(identity.actor(auth),id,key);}
    @GetMapping("/consents")public List<PrivacyService.Consent> consents(Authentication auth,@RequestParam UUID projectId){return privacy.consents(identity.actor(auth),projectId);}
    @PostMapping("/consents")@ResponseStatus(HttpStatus.CREATED)public PrivacyService.Consent grant(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody PrivacyService.ConsentInput input){return privacy.grant(identity.actor(auth),key,input);}
    @PostMapping("/consents/{id}/withdraw")public PrivacyService.Consent withdraw(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody Reason input){return privacy.withdraw(identity.actor(auth),id,key,etag,input.reason());}
    @GetMapping("/requests")public List<PrivacyService.PrivacyRequest> requests(Authentication auth){return privacy.requests(identity.actor(auth));}
    @PostMapping("/requests")@ResponseStatus(HttpStatus.CREATED)public PrivacyService.PrivacyRequest request(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody PrivacyService.RequestInput input){return privacy.request(identity.actor(auth),key,input);}
    @PostMapping("/requests/{id}/resolve")public PrivacyService.PrivacyRequest resolve(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody Reason input){return privacy.resolve(identity.actor(auth),id,key,input.reason());}
}
