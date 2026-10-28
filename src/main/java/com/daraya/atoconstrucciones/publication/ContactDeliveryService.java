package com.daraya.atoconstrucciones.publication;
import com.daraya.atoconstrucciones.integrations.mail.ContactMailPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
@Service
public class ContactDeliveryService {
    public record Lease(UUID contactId,UUID token){}
    private final JdbcTemplate jdbc;private final ContactMailPort mail;
    public ContactDeliveryService(JdbcTemplate jdbc,ContactMailPort mail){this.jdbc=jdbc;this.mail=mail;}
    @Transactional public List<Lease> claim(){
        if(!mail.available())return List.of(); // Config pendiente: conservar PENDING sin agotar reintentos.
        jdbc.update("UPDATE contact_delivery SET state='DEAD',error_code='LEASE_EXHAUSTED',lease_until=NULL,lease_token=NULL WHERE state='RUNNING' AND lease_until<now() AND attempts>=5");
        UUID token=UUID.randomUUID();return jdbc.query("""
          WITH candidate AS (SELECT contact_id FROM contact_delivery WHERE attempts<5 AND
            ((state='PENDING' AND available_at<=now()) OR (state='RUNNING' AND lease_until<now()))
            ORDER BY available_at FOR UPDATE SKIP LOCKED LIMIT 1)
          UPDATE contact_delivery d SET state='RUNNING',attempts=attempts+1,lease_token=?,lease_until=now()+interval '60 seconds'
          FROM candidate c WHERE d.contact_id=c.contact_id RETURNING d.contact_id
          """,(r,n)->new Lease(r.getObject(1,UUID.class),token),token);
    }
    public void send(Lease lease){
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM contact_delivery WHERE contact_id=? AND lease_token=? AND state='RUNNING' AND lease_until>now())",Boolean.class,lease.contactId(),lease.token())))return;
        mail.notify(lease.contactId());
    }
    @Transactional public void complete(Lease lease){jdbc.update("UPDATE contact_delivery SET state='SENT',sent_at=now(),lease_token=NULL,lease_until=NULL,error_code=NULL WHERE contact_id=? AND lease_token=? AND state='RUNNING' AND lease_until>now()",lease.contactId(),lease.token());}
    @Transactional public void fail(Lease lease){jdbc.update("UPDATE contact_delivery SET state=CASE WHEN attempts>=5 THEN 'DEAD' ELSE 'PENDING' END,available_at=now()+make_interval(secs=>least(300,power(2,attempts)::integer)+floor(random()*3)::integer),lease_token=NULL,lease_until=NULL,error_code='SMTP_FAILED' WHERE contact_id=? AND lease_token=? AND state='RUNNING'",lease.contactId(),lease.token());}
}
