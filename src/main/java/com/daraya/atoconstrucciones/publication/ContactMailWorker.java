package com.daraya.atoconstrucciones.publication;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component @ConditionalOnProperty(name="ato.contact.worker-enabled",havingValue="true",matchIfMissing=true)
public class ContactMailWorker {
    private final ContactDeliveryService delivery;
    public ContactMailWorker(ContactDeliveryService delivery){this.delivery=delivery;}
    @Scheduled(fixedDelayString="${ato.contact.poll-ms:2000}")public void poll(){for(var lease:delivery.claim()){try{delivery.send(lease);delivery.complete(lease);}catch(RuntimeException e){delivery.fail(lease);}}}
}
