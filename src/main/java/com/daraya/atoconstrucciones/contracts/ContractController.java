package com.daraya.atoconstrucciones.contracts;
import com.daraya.atoconstrucciones.identity.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/projects/{projectId}")
public class ContractController {
    public record Progress(@NotNull @Pattern(regexp="EN_EJECUCION|TERMINADO")String state){}
    private final ContractService contracts;private final IdentityService identity;
    public ContractController(ContractService contracts,IdentityService identity){this.contracts=contracts;this.identity=identity;}
    private ResponseEntity<ContractService.Contract> contract(ContractService.Contract c){return ResponseEntity.ok().eTag("\""+c.version()+"\"").cacheControl(CacheControl.noStore()).body(c);}
    private ResponseEntity<ContractService.Extra> extra(ContractService.Extra e,int status){return ResponseEntity.status(status).eTag("\""+e.version()+"\"").cacheControl(CacheControl.noStore()).body(e);}
    @GetMapping("/contract")public ResponseEntity<ContractService.Contract> read(Authentication auth,@PathVariable UUID projectId,@RequestParam(required=false)java.time.Instant asOf){return contract(contracts.read(identity.actor(auth),projectId,asOf));}
    @PutMapping("/contract")public ResponseEntity<ContractService.Contract> save(Authentication auth,@PathVariable UUID projectId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody ContractService.Input input){return contract(contracts.save(identity.actor(auth),projectId,key,etag,input));}
    @PostMapping("/contract/formalize")public ResponseEntity<ContractService.Contract> formalize(Authentication auth,@PathVariable UUID projectId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody ContractService.Evidence input){return contract(contracts.formalize(identity.actor(auth),projectId,key,etag,input));}
    @PostMapping("/contract/amendments")public ResponseEntity<ContractService.Contract> adjust(Authentication auth,@PathVariable UUID projectId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody ContractService.Adjustment input){return contract(contracts.adjust(identity.actor(auth),projectId,key,etag,input));}
    @GetMapping("/extras")public ContractService.ExtraPage extras(Authentication auth,@PathVariable UUID projectId){return contracts.extras(identity.actor(auth),projectId);}
    @GetMapping("/extras/{extraId}")public ResponseEntity<ContractService.Extra> readExtra(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID extraId){return extra(contracts.readExtra(identity.actor(auth),projectId,extraId),200);}
    @PostMapping("/extras")public ResponseEntity<ContractService.Extra> request(Authentication auth,@PathVariable UUID projectId,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody ContractService.Request input){return extra(contracts.request(identity.actor(auth),projectId,key,input),201);}
    @PostMapping("/extras/{extraId}/revisions")public ResponseEntity<ContractService.Extra> revise(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID extraId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody ContractService.Revision input){return extra(contracts.revise(identity.actor(auth),projectId,extraId,key,etag,input),201);}
    @PostMapping("/extras/{extraId}/acceptances")public ResponseEntity<ContractService.Extra> accept(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID extraId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody ContractService.Evidence input){return extra(contracts.accept(identity.actor(auth),projectId,extraId,key,etag,input),201);}
    @PostMapping("/extras/{extraId}/approve")public ResponseEntity<ContractService.Extra> approve(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID extraId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody ContractService.Evidence input){return extra(contracts.approve(identity.actor(auth),projectId,extraId,key,etag,input),200);}
    @PostMapping("/extras/{extraId}/cancel")public ResponseEntity<ContractService.Extra> cancel(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID extraId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody ContractService.Adjustment input){return extra(contracts.cancel(identity.actor(auth),projectId,extraId,key,etag,input),200);}
    @PostMapping("/extras/{extraId}/progress")public ResponseEntity<ContractService.Extra> progress(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID extraId,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody Progress input){return extra(contracts.progress(identity.actor(auth),projectId,extraId,key,etag,input.state()),200);}
}
