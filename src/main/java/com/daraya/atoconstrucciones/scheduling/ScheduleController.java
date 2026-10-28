package com.daraya.atoconstrucciones.scheduling;

import com.daraya.atoconstrucciones.identity.IdentityService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.UUID;
import static com.daraya.atoconstrucciones.scheduling.ScheduleModels.*;

@RestController
@RequestMapping("/api/v1/projects/{projectId}")
public class ScheduleController {
    private final ScheduleService service;
    private final IdentityService identity;
    public ScheduleController(ScheduleService service,IdentityService identity){this.service=service;this.identity=identity;}
    private <T> ResponseEntity<T> response(T body,long revision){return ResponseEntity.ok().eTag("\""+revision+"\"").cacheControl(CacheControl.noStore()).body(body);}
    @GetMapping("/schedule") public ResponseEntity<Schedule> read(Authentication auth,@PathVariable UUID projectId){var s=service.read(identity.actor(auth),projectId);return response(s,s.revision());}
    @GetMapping("/schedule/tasks") public ResponseEntity<AssignedTasks> assigned(Authentication auth,@PathVariable UUID projectId){var s=service.assigned(identity.actor(auth),projectId);return response(s,s.revision());}
    @GetMapping("/progress-summary") public ResponseEntity<ProgressSummary> progress(Authentication auth,@PathVariable UUID projectId,@RequestParam(required=false) Instant asOf){var s=service.progress(identity.actor(auth),projectId,asOf);return response(s,s.scheduleRevision());}
    @PostMapping("/schedule/changes") public ResponseEntity<Schedule> changes(Authentication auth,@PathVariable UUID projectId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String etag,@Valid @RequestBody Changes input){var s=service.change(identity.actor(auth),projectId,key,etag,input);return response(s,s.revision());}
    @PostMapping("/schedule/progress-baselines") public ResponseEntity<Schedule> baseline(Authentication auth,@PathVariable UUID projectId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String etag,@Valid @RequestBody BaselineInput input){var s=service.createBaseline(identity.actor(auth),projectId,key,etag,input);return response(s,s.revision());}
    @PostMapping("/schedule/progress-baselines/{baselineId}/approve") public ResponseEntity<Schedule> approve(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID baselineId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String etag,@Valid @RequestBody Decision input){var s=service.approveBaseline(identity.actor(auth),projectId,baselineId,key,etag,input);return response(s,s.revision());}
    @PostMapping("/schedule/tasks/{taskId}/progress-reports") public ResponseEntity<AssignedTasks> report(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID taskId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String etag,@Valid @RequestBody ReportInput input){var s=service.report(identity.actor(auth),projectId,taskId,key,etag,input);return response(s,s.revision());}
    @PostMapping("/schedule/progress-reports/{reportId}/verify") public ResponseEntity<Schedule> verify(Authentication auth,@PathVariable UUID projectId,@PathVariable UUID reportId,@RequestHeader(value="Idempotency-Key",required=false) String key,@RequestHeader(value="If-Match",required=false) String etag,@Valid @RequestBody Verification input){var s=service.verify(identity.actor(auth),projectId,reportId,key,etag,input);return response(s,s.revision());}
}
