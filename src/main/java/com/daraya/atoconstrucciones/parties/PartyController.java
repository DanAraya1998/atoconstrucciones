package com.daraya.atoconstrucciones.parties;
import com.daraya.atoconstrucciones.identity.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1")
public class PartyController {
    private final PartyService parties;private final IdentityService identity;
    public PartyController(PartyService parties,IdentityService identity){this.parties=parties;this.identity=identity;}
    private String kind(String collection){return collection.equals("clients")?"CLIENT":"SUPPLIER";}
    private ResponseEntity<PartyService.Party> response(PartyService.Party p,int status){return ResponseEntity.status(status).eTag("\""+p.version()+"\"").cacheControl(CacheControl.noStore()).body(p);}
    @GetMapping("/{collection:clients|suppliers}") public PartyService.Page list(Authentication auth,@PathVariable String collection,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size,@RequestParam(required=false)SupplierCategory supplierCategory){return parties.list(identity.actor(auth),kind(collection),page,size,supplierCategory);}
    @GetMapping("/{collection:clients|suppliers}/{id}") public ResponseEntity<PartyService.Party> read(Authentication auth,@PathVariable String collection,@PathVariable UUID id){return response(parties.read(identity.actor(auth),id,kind(collection)),200);}
    @PostMapping("/{collection:clients|suppliers}") public ResponseEntity<PartyService.Party> create(Authentication auth,@PathVariable String collection,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody PartyService.Input input){return response(parties.create(identity.actor(auth),kind(collection),key,input),201);}
    @PatchMapping("/{collection:clients|suppliers}/{id}") public ResponseEntity<PartyService.Party> edit(Authentication auth,@PathVariable String collection,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody PartyService.Input input){return response(parties.edit(identity.actor(auth),id,kind(collection),key,etag,input),200);}
    @GetMapping("/{collection:clients|suppliers}/{id}/members") public java.util.List<PartyService.Member> members(Authentication auth,@PathVariable String collection,@PathVariable UUID id){return parties.members(identity.actor(auth),id,kind(collection));}
    @GetMapping("/{collection:clients|suppliers}/{id}/member-accounts") public ResponseEntity<PartyService.MemberPage> memberAccounts(Authentication auth,@PathVariable String collection,@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(parties.memberAccounts(identity.actor(auth),id,kind(collection),page,size));}
    @PutMapping("/{collection:clients|suppliers}/{id}/members") public ResponseEntity<PartyService.Party> member(Authentication auth,@PathVariable String collection,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody PartyService.Member input){return response(parties.member(identity.actor(auth),id,kind(collection),key,etag,input),200);}
}
