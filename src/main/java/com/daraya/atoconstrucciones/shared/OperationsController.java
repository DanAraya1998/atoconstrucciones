package com.daraya.atoconstrucciones.shared;
import com.daraya.atoconstrucciones.identity.IdentityService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
@RestController
public class OperationsController {
    public record Status(int failedJobs,int failedContactNotifications,int overdueScans,boolean attentionRequired){}
    private final IdentityService identity;private final JdbcTemplate jdbc;
    public OperationsController(IdentityService identity,JdbcTemplate jdbc){this.identity=identity;this.jdbc=jdbc;}
    @GetMapping("/api/v1/operations/status") public ResponseEntity<Status> status(Authentication authentication){
        var actor=identity.actor(authentication);if(!actor.manager()&&!actor.roles().contains("ADMIN"))throw ApiException.forbidden();
        int jobs=jdbc.queryForObject("SELECT count(*) FROM outbox_job WHERE state='DEAD'",Integer.class),mail=jdbc.queryForObject("SELECT count(*) FROM contact_delivery WHERE state='DEAD'",Integer.class),scans=jdbc.queryForObject("SELECT count(*) FROM document WHERE state='QUARANTINED' AND created_at<now()-interval '30 minutes'",Integer.class);
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(new Status(jobs,mail,scans,jobs+mail+scans>0));
    }
}
