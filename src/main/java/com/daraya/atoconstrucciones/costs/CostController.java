package com.daraya.atoconstrucciones.costs;
import com.daraya.atoconstrucciones.identity.IdentityService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class CostController {
    private final CostService costs;private final IdentityService identity;
    public CostController(CostService costs,IdentityService identity) {this.costs=costs;this.identity=identity;}
    private ResponseEntity<CostService.Expense> result(CostService.Expense e,int status) {var b=ResponseEntity.status(status).cacheControl(CacheControl.noStore()).eTag("\""+e.version()+"\"");if(status==201)b.location(URI.create("/api/v1/expenses/"+e.id()));return b.body(e);}
    @PostMapping("/expenses") public ResponseEntity<CostService.Expense> create(Authentication a,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody CostService.Input i) {return result(costs.create(identity.actor(a),key,i),201);}
    @GetMapping("/expenses/{id}") public ResponseEntity<CostService.Expense> read(Authentication a,@PathVariable UUID id) {return result(costs.read(identity.actor(a),id),200);}
    @PatchMapping("/expenses/{id}") public ResponseEntity<CostService.Expense> edit(Authentication a,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody CostService.Input i) {return result(costs.edit(identity.actor(a),id,key,etag,i),200);}
    @GetMapping("/projects/{p}/expenses") public List<CostService.ProjectExpense> list(Authentication a,@PathVariable UUID p,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="30")int size) {return costs.list(identity.actor(a),p,page,size);}
    @PostMapping("/expenses/{id}/{action:review|validate|reject|reverse}") public ResponseEntity<CostService.Expense> decide(Authentication a,@PathVariable UUID id,@PathVariable String action,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody CostService.Decision i) {return result(costs.decide(identity.actor(a),id,key,etag,action,i),200);}
}
