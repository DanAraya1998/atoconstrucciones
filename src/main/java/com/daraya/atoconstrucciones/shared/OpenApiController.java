package com.daraya.atoconstrucciones.shared;

import com.daraya.atoconstrucciones.identity.IdentityService;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class OpenApiController {
    private final IdentityService identity;
    public OpenApiController(IdentityService identity) { this.identity=identity; }
    @GetMapping("/api/v1/openapi.json")
    public ResponseEntity<Resource> specification(Authentication auth) {
        identity.actor(auth);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(new ClassPathResource("openapi/m4.json"));
    }
}
