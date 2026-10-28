package com.daraya.atoconstrucciones.documents.fiscal;
import com.daraya.atoconstrucciones.identity.IdentityService;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;
@RestController @RequestMapping("/api/v1")
public class FiscalController {
    private final FiscalService fiscal;private final IdentityService identity;
    public FiscalController(FiscalService fiscal,IdentityService identity) {this.fiscal=fiscal;this.identity=identity;}
    @PostMapping("/fiscal-vouchers/imports") public ResponseEntity<FiscalService.Voucher> importXml(Authentication a,@RequestHeader(value="Idempotency-Key",required=false)String key,@Valid @RequestBody FiscalService.Import i) {var v=fiscal.importXml(identity.actor(a),key,i);return ResponseEntity.created(URI.create("/api/v1/fiscal-vouchers/"+v.id())).cacheControl(CacheControl.noStore()).body(v);}
    @GetMapping("/fiscal-vouchers/{id}") public FiscalService.Voucher read(Authentication a,@PathVariable UUID id) {return fiscal.read(identity.actor(a),id);}
    @GetMapping("/projects/{p}/fiscal-vouchers") public List<FiscalService.Voucher> list(Authentication a,@PathVariable UUID p,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="30")int size) {return fiscal.list(identity.actor(a),p,page,size);}
}
