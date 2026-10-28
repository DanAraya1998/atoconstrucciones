package com.daraya.atoconstrucciones.publication;
import com.daraya.atoconstrucciones.shared.ApiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
@Service
public class ContactRateLimiter {
    private final JdbcTemplate jdbc;private final String secret;private final int max;
    public ContactRateLimiter(JdbcTemplate jdbc,@Value("${ato.contact.rate-secret:local-development-only}")String secret,@Value("${ato.contact.rate-max:8}")int max){this.jdbc=jdbc;this.secret=secret.isBlank()?"local-development-only":secret;this.max=max;}
    @Transactional(propagation=Propagation.REQUIRES_NEW,noRollbackFor=ApiException.class) public void check(String source){
        String fingerprint;
        try{var mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));fingerprint=HexFormat.of().formatHex(mac.doFinal(source.getBytes(StandardCharsets.UTF_8)));}catch(Exception e){throw new IllegalStateException(e);}
        long bucket=java.time.Instant.now().getEpochSecond()/600;
        int count=jdbc.queryForObject("INSERT INTO contact_rate_limit(fingerprint,bucket,attempts) VALUES (?,?,1) ON CONFLICT(fingerprint,bucket) DO UPDATE SET attempts=contact_rate_limit.attempts+1 RETURNING attempts",Integer.class,fingerprint,bucket);
        // El límite no se revierte al rechazar la solicitud.
        if(count>max)throw new ApiException(429,"CONTACT_RATE_LIMIT","Se alcanzó el límite. Intente nuevamente en diez minutos.");
        jdbc.update("DELETE FROM contact_rate_limit WHERE bucket<?",bucket-144);
    }
}
