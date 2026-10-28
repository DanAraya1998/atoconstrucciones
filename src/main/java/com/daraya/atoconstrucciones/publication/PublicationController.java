package com.daraya.atoconstrucciones.publication;
import com.daraya.atoconstrucciones.identity.IdentityService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class PublicationController {
    private final com.daraya.atoconstrucciones.documents.PrivacyService privacy;private final PublicationService publication;private final SiteContentService site;private final ContactService contacts;private final IdentityService identity;
    public PublicationController(PublicationService publication,SiteContentService site,ContactService contacts,IdentityService identity,com.daraya.atoconstrucciones.documents.PrivacyService privacy){this.privacy=privacy;this.publication=publication;this.site=site;this.contacts=contacts;this.identity=identity;}
    private ResponseEntity<PublicationService.Publication> response(PublicationService.Publication p,int status){return ResponseEntity.status(status).cacheControl(CacheControl.noStore()).eTag("\""+p.version()+"\"").body(p);}
    @GetMapping("/publication-consents")public List<com.daraya.atoconstrucciones.documents.PrivacyService.Consent> consents(Authentication auth,@RequestParam UUID projectId){return privacy.websiteConsents(identity.actor(auth),projectId);}
    @GetMapping("/publications")public List<PublicationService.Publication> list(Authentication auth,@RequestParam(required=false)UUID projectId){return publication.list(identity.actor(auth),projectId);}
    @GetMapping("/publications/{id}")public ResponseEntity<PublicationService.Publication> read(Authentication auth,@PathVariable UUID id){return response(publication.read(identity.actor(auth),id),200);}
    @PostMapping("/publications")public ResponseEntity<PublicationService.Publication> create(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody PublicationService.Input input){return response(publication.create(identity.actor(auth),key,input),201);}
    @PutMapping("/publications/{id}")public ResponseEntity<PublicationService.Publication> edit(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody PublicationService.Input input){return response(publication.edit(identity.actor(auth),id,key,etag,input),200);}
    @PostMapping("/publications/{id}/publish")public ResponseEntity<PublicationService.Publication> publish(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody PublicationService.Decision input){return response(publication.publish(identity.actor(auth),id,key,etag,input),200);}
    @PostMapping("/publications/{id}/withdraw")public ResponseEntity<PublicationService.Publication> withdraw(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody PublicationService.Reason input){return response(publication.withdraw(identity.actor(auth),id,key,etag,input),200);}
    @GetMapping("/publications/{id}/assets/{assetId}/preview")public ResponseEntity<byte[]> preview(Authentication auth,@PathVariable UUID id,@PathVariable UUID assetId){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.IMAGE_JPEG).body(publication.preview(identity.actor(auth),id,assetId));}
    @GetMapping("/publication-site")public SiteContentService.Content site(Authentication auth){return site.read(identity.actor(auth));}
    @PutMapping("/publication-site")public SiteContentService.Content siteEdit(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody SiteContentService.Input input){return site.edit(identity.actor(auth),key,etag,input);}
    @PostMapping("/publication-site/approve")public SiteContentService.Content siteApprove(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag){return site.approve(identity.actor(auth),key,etag);}
    @GetMapping("/contacts")public List<ContactService.Contact> contacts(Authentication auth,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return contacts.list(identity.actor(auth),page,size);}
    @PutMapping("/contacts/delegates")public ContactService.Delegate delegate(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody ContactService.Delegate input){return contacts.delegate(identity.actor(auth),key,input);}
    @PostMapping("/contacts/{id}/retry")public ContactService.Receipt retry(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key){return contacts.retry(identity.actor(auth),id,key);}
}
