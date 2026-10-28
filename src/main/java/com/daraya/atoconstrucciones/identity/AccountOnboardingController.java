package com.daraya.atoconstrucciones.identity;

import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController
@RequestMapping("/api/v1/accounts")
public class AccountOnboardingController {
    private final IdentityService identity;private final AccountOnboardingService accounts;
    public AccountOnboardingController(IdentityService identity,AccountOnboardingService accounts){this.identity=identity;this.accounts=accounts;}
    private ResponseEntity<AccountOnboardingService.Account> response(AccountOnboardingService.Account account){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).eTag("\""+account.version()+"\"").body(account);}
    @GetMapping("/me") public ResponseEntity<AccountOnboardingService.Account> self(Authentication auth){return response(accounts.self(identity.actor(auth)));}
    @GetMapping("/capabilities") public ResponseEntity<Map<String,Boolean>> capabilities(Authentication auth){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("directAvailable",accounts.directAvailable(identity.actor(auth))));}
    @PostMapping("/management-search") public ResponseEntity<AccountOnboardingService.Page> search(Authentication auth,@Valid @RequestBody AccountOnboardingService.Search input){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.search(identity.actor(auth),input));}
    @GetMapping("/{id}") public ResponseEntity<AccountOnboardingService.Account> read(Authentication auth,@PathVariable UUID id){return response(accounts.read(identity.actor(auth),id));}
    @PostMapping public ResponseEntity<AccountOnboardingService.Account> create(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody AccountOnboardingService.Direct input){
        var account=accounts.create(identity.actor(auth),key,input);return ResponseEntity.created(java.net.URI.create("/api/v1/accounts/"+account.id())).cacheControl(CacheControl.noStore()).eTag("\""+account.version()+"\"").body(account);
    }
    @PostMapping("/{id}/approve") public ResponseEntity<AccountOnboardingService.Account> approve(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String version,@Valid @RequestBody AccountOnboardingService.Access input){return response(accounts.approve(identity.actor(auth),id,key,version,input));}
    @PostMapping("/{id}/{action:reject|suspend|resume}") public ResponseEntity<AccountOnboardingService.Account> decide(Authentication auth,@PathVariable UUID id,@PathVariable String action,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String version,@Valid @RequestBody AccountOnboardingService.Decision input){return response(accounts.decide(identity.actor(auth),id,action,key,version,input));}
    @PostMapping("/{id}/activation") public ResponseEntity<AccountOnboardingService.Account> retry(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String version){return response(accounts.retryActivation(identity.actor(auth),id,key,version));}
}
