package com.daraya.atoconstrucciones.projects;

import com.daraya.atoconstrucciones.contracts.ContractService;
import com.daraya.atoconstrucciones.identity.IdentityService;
import com.daraya.atoconstrucciones.treasury.TreasuryService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/projects/{projectId}/client-portal")
public class ClientPortalController {
    private final ClientPortalService service;private final IdentityService identity;
    public ClientPortalController(ClientPortalService service,IdentityService identity){this.service=service;this.identity=identity;}
    private <T> ResponseEntity<T> response(T value){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(value);}
    @GetMapping public ResponseEntity<ClientPortalService.Portal> read(Authentication auth,@PathVariable UUID projectId,@RequestParam(defaultValue="0") int depositPage){return response(service.read(identity.actor(auth),projectId,depositPage));}
    @PostMapping("/deposit-reports") public ResponseEntity<ClientPortalService.ClientDeposit> report(Authentication auth,@PathVariable UUID projectId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody TreasuryService.DepositInput input){return response(service.report(identity.actor(auth),projectId,key,input));}
    @PostMapping("/extras") public ResponseEntity<ClientPortalService.ClientExtra> request(Authentication auth,@PathVariable UUID projectId,@RequestHeader(value="Idempotency-Key",required=false) String key,@Valid @RequestBody ContractService.Request input){return response(service.request(identity.actor(auth),projectId,key,input));}
    @PostMapping("/extras/{extraId}/acceptances") public ResponseEntity<ClientPortalService.ClientExtra> accept(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID extraId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String etag,@Valid @RequestBody ContractService.Evidence input){return response(service.accept(identity.actor(auth),projectId,extraId,key,etag,input));}
}
