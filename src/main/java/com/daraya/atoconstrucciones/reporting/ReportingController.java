package com.daraya.atoconstrucciones.reporting;
import com.daraya.atoconstrucciones.identity.IdentityService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.time.Instant;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class ReportingController {
    private final ReportingService reports;private final IdentityService identity;
    public ReportingController(ReportingService reports,IdentityService identity) {this.reports=reports;this.identity=identity;}
    @GetMapping("/projects/{p}/financial-summary") public ReportingService.Summary project(Authentication a,@PathVariable UUID p,@RequestParam(required=false)Instant asOf) {return reports.project(identity.actor(a),p,asOf);}
    @GetMapping("/projects/{p}/client-summary") public ReportingService.ClientSummary client(Authentication a,@PathVariable UUID p,@RequestParam(required=false)Instant asOf) {return reports.client(identity.actor(a),p,asOf);}
    @GetMapping("/reports/business-summary") public ReportingService.Business business(Authentication a,@RequestParam(required=false)Instant asOf) {return reports.business(identity.actor(a),asOf);}
    @GetMapping("/operations/collection-followups") public List<ReportingService.Followup> followups(Authentication a,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="30")int size) {return reports.followups(identity.actor(a),page,size);}
}
