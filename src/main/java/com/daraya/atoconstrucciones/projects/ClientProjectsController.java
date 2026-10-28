package com.daraya.atoconstrucciones.projects;

import com.daraya.atoconstrucciones.identity.IdentityService;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
public class ClientProjectsController {
    private final ClientPortalService service;private final IdentityService identity;
    public ClientProjectsController(ClientPortalService service,IdentityService identity){this.service=service;this.identity=identity;}
    @GetMapping("/api/v1/client-projects") public ResponseEntity<ClientPortalService.ClientProjects> list(Authentication auth,@RequestParam(defaultValue="0") int page,@RequestParam(defaultValue="20") int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(service.list(identity.actor(auth),page,size));}
}
