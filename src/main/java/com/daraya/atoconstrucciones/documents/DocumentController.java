package com.daraya.atoconstrucciones.documents;
import com.daraya.atoconstrucciones.identity.IdentityService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
@RestController @RequestMapping("/api/v1")
public class DocumentController {
    private final DocumentService documents;private final IdentityService identity;
    public DocumentController(DocumentService documents,IdentityService identity){this.documents=documents;this.identity=identity;}
    private ResponseEntity<DocumentService.Document> response(DocumentService.Document d,int status){return ResponseEntity.status(status).eTag("\""+d.version()+"\"").cacheControl(CacheControl.noStore()).body(d);}
    @PostMapping("/documents/upload-sessions")public ResponseEntity<DocumentService.UploadSession> reserve(Authentication auth,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody DocumentService.Reservation input){return ResponseEntity.status(201).cacheControl(CacheControl.noStore()).body(documents.reserve(identity.actor(auth),key,input));}
    @PutMapping(value="/documents/{id}/content",consumes="application/octet-stream")public ResponseEntity<DocumentService.Document> upload(Authentication auth,@PathVariable UUID id,HttpServletRequest request)throws IOException{return response(documents.upload(identity.actor(auth),id,request.getInputStream(),request.getContentLengthLong()),202);}
    @PostMapping("/documents/{id}/complete-upload")public ResponseEntity<DocumentService.Document> complete(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key){return response(documents.complete(identity.actor(auth),id,key),202);}
    @GetMapping("/documents/{id}")public ResponseEntity<DocumentService.Document> read(Authentication auth,@PathVariable UUID id){return response(documents.read(identity.actor(auth),id),200);}
    @GetMapping("/projects/{id}/documents")public DocumentService.Page list(Authentication auth,@PathVariable UUID id){return documents.list(identity.actor(auth),id);}
    @PutMapping("/documents/{id}/sharing")public ResponseEntity<DocumentService.Document> share(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@RequestHeader(value="If-Match",required=false)String etag,@Valid @RequestBody DocumentService.Sharing input){return response(documents.share(identity.actor(auth),id,key,etag,input),200);}
    @PostMapping("/documents/{id}/links")public ResponseEntity<DocumentService.Document> link(Authentication auth,@PathVariable UUID id,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody DocumentService.Link input){return response(documents.link(identity.actor(auth),id,key,input),201);}
    @GetMapping("/documents/{id}/content")public ResponseEntity<byte[]> download(Authentication auth,@PathVariable UUID id){
        var download=documents.download(identity.actor(auth),id);return ResponseEntity.ok().cacheControl(CacheControl.noStore())
            .header("X-Content-Type-Options","nosniff").header("Content-Security-Policy","sandbox; default-src 'none'")
            .header("Content-Disposition",ContentDisposition.attachment().filename(download.document().filename(),StandardCharsets.UTF_8).build().toString())
            .contentType(MediaType.parseMediaType(download.document().mediaType())).body(download.bytes());
    }
}
