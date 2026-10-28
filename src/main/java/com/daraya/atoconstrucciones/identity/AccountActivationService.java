package com.daraya.atoconstrucciones.identity;

import com.daraya.atoconstrucciones.audit.AuditService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.util.*;

/** Cola durable. Un timeout tras entregar correo puede duplicar el envío al reintentar. */
@Service
public class AccountActivationService {
    private final JdbcTemplate jdbc;private final AccountProvisioningPort provisioning;private final AuditService audit;
    private final TransactionTemplate tx;private final boolean enabled;
    public AccountActivationService(JdbcTemplate jdbc,AccountProvisioningPort provisioning,AuditService audit,PlatformTransactionManager manager,
        @Value("${ato.accounts.activation-worker-enabled:true}") boolean enabled){this.jdbc=jdbc;this.provisioning=provisioning;this.audit=audit;this.tx=new TransactionTemplate(manager);this.enabled=enabled;}
    private record Delivery(UUID userId,UUID requestedBy,String issuer,String subject,UUID token,int attempt) {}
    @Scheduled(fixedDelayString="${ato.accounts.activation-poll-ms:2000}")
    public void poll(){if(enabled&&provisioning.configured())deliverOne();}
    public boolean deliverOne(){
        Delivery delivery=tx.execute(status->{
            var rows=jdbc.query("""
                SELECT a.*,u.issuer,u.subject,u.decided_by FROM account_activation a JOIN user_account u ON u.id=a.user_id
                WHERE u.enabled AND u.access_state='APPROVED' AND NOT u.email_verified AND a.attempts<5
                  AND ((a.state IN ('PENDING','FAILED') AND a.available_at<=now()) OR (a.state='SENDING' AND a.lease_until<now()))
                ORDER BY a.available_at,a.user_id LIMIT 1 FOR UPDATE OF a SKIP LOCKED
                """,(r,n)->new Delivery(r.getObject("user_id",UUID.class),r.getObject("decided_by",UUID.class),r.getString("issuer"),r.getString("subject"),UUID.randomUUID(),r.getInt("attempts")+1));
            if(rows.isEmpty())return null;var next=rows.getFirst();
            jdbc.update("UPDATE account_activation SET state='SENDING',attempts=?,lease_token=?,lease_until=now()+interval '60 seconds' WHERE user_id=?",next.attempt(),next.token(),next.userId());return next;
        });
        if(delivery==null)return false;
        boolean delivered;
        try{provisioning.sendActivation(delivery.issuer(),delivery.subject());delivered=true;}catch(RuntimeException e){delivered=false;}
        final boolean success=delivered;
        tx.executeWithoutResult(status->{
            int changed=jdbc.update("""
                UPDATE account_activation SET state=?,error_code=?,sent_at=CASE WHEN ? THEN now() ELSE sent_at END,
                    available_at=now()+(?*interval '1 second'),lease_until=NULL,lease_token=NULL
                WHERE user_id=? AND state='SENDING' AND lease_token=? AND lease_until>now()
                """,success?"SENT":"FAILED",success?null:"ACTIVATION_DELIVERY_FAILED",success,Math.min(3600,30*(1<<delivery.attempt())),delivery.userId(),delivery.token());
            if(changed==1)audit.record(delivery.requestedBy(),success?"ACCOUNT_ACTIVATION_SENT":"ACCOUNT_ACTIVATION_FAILED",delivery.userId());
        });return true;
    }
}
