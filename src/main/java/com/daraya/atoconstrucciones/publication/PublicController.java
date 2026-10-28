package com.daraya.atoconstrucciones.publication;
import com.daraya.atoconstrucciones.documents.PrivacyService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;
@RestController @RequestMapping("/api/v1/public")
public class PublicController {
    private final PublicationService publication;private final ContactService contacts;private final PrivacyService privacy;private final SiteContentService site;
    public PublicController(PublicationService publication,ContactService contacts,PrivacyService privacy,SiteContentService site){this.publication=publication;this.contacts=contacts;this.privacy=privacy;this.site=site;}
    @GetMapping("/site")public ResponseEntity<SiteContentService.PublicContent> site(){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(site.publicRead());}
    @GetMapping("/projects")public ResponseEntity<PublicationService.PublicPage> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publication.publicList(page,size));}
    @GetMapping("/projects/{slug}")public ResponseEntity<PublicationService.PublicProject> read(@PathVariable String slug){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(publication.publicRead(slug));}
    @GetMapping("/assets/{id}")public ResponseEntity<byte[]> asset(@PathVariable UUID id){return ResponseEntity.ok().cacheControl(CacheControl.noStore()).header("X-Content-Type-Options","nosniff").header("Content-Disposition","inline; filename=portfolio.jpg").contentType(MediaType.IMAGE_JPEG).body(publication.publicAsset(id));}
    @GetMapping("/privacy-notice")public ResponseEntity<PrivacyService.Notice> notice(){var n=privacy.publicNotice();return ResponseEntity.status(n==null?204:200).cacheControl(CacheControl.noStore()).body(n);}
    @PostMapping("/contact")public ResponseEntity<ContactService.Receipt> contact(@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody ContactService.Input input){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(contacts.receive(key,input));}
}
