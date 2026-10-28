package com.daraya.atoconstrucciones.foundation;

import com.daraya.atoconstrucciones.identity.IdentityService;
import com.daraya.atoconstrucciones.shared.jobs.JobService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class FoundationController {
    public record Member(@NotNull UUID userId, boolean active) {}
    public record Name(@NotBlank @Size(max=120) String name) {}
    private final FoundationService resources;
    private final IdentityService identity;
    private final JobService jobs;
    public FoundationController(FoundationService resources, IdentityService identity, JobService jobs) { this.resources=resources; this.identity=identity; this.jobs=jobs; }
    @GetMapping("/foundation/resources")
    public ResponseEntity<FoundationService.Page> list(Authentication auth, @RequestParam(defaultValue="0") int page, @RequestParam(defaultValue="20") int size) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(resources.list(identity.actor(auth),page,size));
    }
    @GetMapping("/foundation/resources/{id}")
    public ResponseEntity<FoundationService.Resource> read(Authentication auth, @PathVariable UUID id) {
        var resource=resources.read(identity.actor(auth),id);
        return ResponseEntity.ok().eTag("\""+resource.version()+"\"").cacheControl(CacheControl.noStore()).body(resource);
    }
    @PostMapping("/foundation/resources")
    public ResponseEntity<FoundationService.Resource> create(Authentication auth, @RequestHeader(value="Idempotency-Key",required=false) String key, @Valid @RequestBody Name input) {
        var resource=resources.create(identity.actor(auth),key,input.name());
        return ResponseEntity.created(URI.create("/api/v1/foundation/resources/"+resource.id())).eTag("\""+resource.version()+"\"").cacheControl(CacheControl.noStore()).body(resource);
    }
    @PatchMapping("/foundation/resources/{id}")
    public ResponseEntity<FoundationService.Resource> rename(Authentication auth, @PathVariable UUID id,
            @RequestHeader(value="Idempotency-Key",required=false) String key, @RequestHeader(value="If-Match",required=false) String etag, @Valid @RequestBody Name input) {
        var resource=resources.rename(identity.actor(auth),id,key,etag,input.name());
        return ResponseEntity.ok().eTag("\""+resource.version()+"\"").cacheControl(CacheControl.noStore()).body(resource);
    }
    @PostMapping("/foundation/resources/{id}/receipts")
    public ResponseEntity<FoundationService.Queued> enqueue(Authentication auth, @PathVariable UUID id, @RequestHeader(value="Idempotency-Key",required=false) String key) {
        var job=resources.enqueue(identity.actor(auth),id,key);
        return ResponseEntity.accepted().location(URI.create("/api/v1/jobs/"+job.id())).cacheControl(CacheControl.noStore()).body(job);
    }
    @PutMapping("/foundation/resources/{id}/members") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void member(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Member input) {
        var actor=identity.actor(auth);
        actor.requireManager();
        resources.read(actor,id);
        identity.setMembership(actor,id,input.userId(),input.active());
    }
    @GetMapping("/jobs/{id}")
    public ResponseEntity<JobService.Job> job(Authentication auth, @PathVariable UUID id) { return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(jobs.read(identity.actor(auth),id)); }
}
