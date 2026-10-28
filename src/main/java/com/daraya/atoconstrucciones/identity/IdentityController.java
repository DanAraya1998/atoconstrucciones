package com.daraya.atoconstrucciones.identity;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import java.util.*;

@RestController
@RequestMapping("/api/v1")
public class IdentityController {
    public record Invite(@NotBlank @Email @Size(max=254) String email, @NotBlank String role) {}
    public record Accept(@NotBlank @Size(max=100) String token) {}
    public record Roles(@NotNull Set<String> roles) {}
    public record Support(@NotNull UUID resourceId, @NotBlank @Size(max=500) String purpose) {}
    public record Reference(UUID id) {}
    private final IdentityService identity;
    private final InvitationService invitations;
    private final SupportService support;
    private final AccountDirectory accounts;
    public IdentityController(IdentityService identity, InvitationService invitations, SupportService support,AccountDirectory accounts) {
        this.identity = identity; this.invitations = invitations; this.support = support;this.accounts=accounts;
    }
    @PostMapping("/accounts/search")
    public ResponseEntity<AccountDirectory.Page> accounts(Authentication auth,@Valid @RequestBody AccountDirectory.Search input){
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(accounts.search(identity.actor(auth),input.query(),input.role(),input.pageValue(),input.sizeValue()));
    }
    @GetMapping("/session")
    public ResponseEntity<Actor> session(Authentication authentication) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(identity.actor(authentication));
    }
    @GetMapping("/session/csrf")
    public ResponseEntity<Map<String,String>> csrf(CsrfToken token) {
        if (token == null) throw new com.daraya.atoconstrucciones.shared.ApiException(400, "COOKIE_SESSION_REQUIRED", "CSRF corresponde a la sesión web.");
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(Map.of("headerName", token.getHeaderName(), "token", token.getToken()));
    }
    @PostMapping("/invitations") @ResponseStatus(HttpStatus.CREATED)
    public ResponseEntity<InvitationService.Invitation> invite(Authentication auth, @Valid @RequestBody Invite input) {
        var invitation = invitations.create(identity.actor(auth), input.email(), input.role());
        return ResponseEntity.created(java.net.URI.create("/api/v1/invitations/" + invitation.id()))
                .cacheControl(CacheControl.noStore()).body(invitation);
    }
    @PostMapping("/invitations/{id}/accept") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void accept(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Accept input) { invitations.accept(identity.actor(auth), id, input.token()); }
    @PutMapping("/users/{id}/roles") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void roles(Authentication auth, @PathVariable UUID id, @Valid @RequestBody Roles input) { identity.setRoles(identity.actor(auth), id, input.roles()); }
    @PostMapping("/users/{id}/disable") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void disable(Authentication auth, @PathVariable UUID id) { identity.disable(identity.actor(auth), id); }
    @PostMapping("/support-access-requests") @ResponseStatus(HttpStatus.CREATED)
    public Reference request(Authentication auth, @Valid @RequestBody Support input) { return new Reference(support.request(identity.actor(auth), input.resourceId(), input.purpose())); }
    @PostMapping("/support-access-requests/{id}/approve") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void approve(Authentication auth, @PathVariable UUID id) { support.approve(identity.actor(auth), id); }
    @PostMapping("/support-grants/{id}/revoke") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(Authentication auth, @PathVariable UUID id) { support.revoke(identity.actor(auth), id); }
}
