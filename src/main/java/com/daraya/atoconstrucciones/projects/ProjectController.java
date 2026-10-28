package com.daraya.atoconstrucciones.projects;
import com.daraya.atoconstrucciones.identity.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

@RestController @RequestMapping("/api/v1/projects")
public class ProjectController {
    public record Reason(@NotNull @Size(max=1000) String reason){}
    private final ProjectService projects;private final IdentityService identity;
    public ProjectController(ProjectService projects,IdentityService identity){this.projects=projects;this.identity=identity;}
    private ResponseEntity<ProjectService.Project> response(ProjectService.Project p,int status){return ResponseEntity.status(status).eTag("\""+p.version()+"\"").cacheControl(CacheControl.noStore()).body(p);}
    @GetMapping public ProjectService.Page list(Authentication auth,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return projects.list(identity.actor(auth),page,size);}
    @GetMapping("/{id}") public ResponseEntity<ProjectService.Project> read(Authentication auth,@PathVariable UUID id){return response(projects.read(identity.actor(auth),id),200);}
    @PostMapping public ResponseEntity<ProjectService.Project> create(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody ProjectService.Input input){return response(projects.create(identity.actor(auth),key,input),201);}
    @PatchMapping("/{id}") public ResponseEntity<ProjectService.Project> edit(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody ProjectService.Edit input){return response(projects.edit(identity.actor(auth),id,key,etag,input),200);}
    @GetMapping("/{id}/members") public java.util.List<ProjectService.Member> members(Authentication auth,@PathVariable UUID id){return projects.members(identity.actor(auth),id);}
    @GetMapping("/{id}/member-accounts") public ResponseEntity<ProjectService.MemberPage> memberAccounts(Authentication auth,@PathVariable UUID id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(projects.memberAccounts(identity.actor(auth),id,page,size));}
    @PostMapping("/{id}/account-candidates") public ResponseEntity<com.daraya.atoconstrucciones.identity.AccountDirectory.Page> accountCandidates(Authentication auth,@PathVariable UUID id,@Valid @RequestBody com.daraya.atoconstrucciones.identity.AccountDirectory.Search input,@RequestParam(defaultValue="CLIENT")String capacity,@RequestParam(required=false)UUID supplierId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(projects.accountCandidates(identity.actor(auth),id,input.query(),capacity,supplierId,input.pageValue(),input.sizeValue()));}
    @PutMapping("/{id}/members") public ResponseEntity<ProjectService.Project> member(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody ProjectService.Member input){return response(projects.member(identity.actor(auth),id,key,etag,input),200);}
    @PostMapping("/{id}/{action:activate|suspend|cancel}") public ResponseEntity<ProjectService.Project> transition(Authentication auth,@PathVariable UUID id,@PathVariable String action,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody Reason input){return response(projects.transition(identity.actor(auth),id,key,etag,action,input.reason()),200);}
}
