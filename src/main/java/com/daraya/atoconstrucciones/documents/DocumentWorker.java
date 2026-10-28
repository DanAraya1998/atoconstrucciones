package com.daraya.atoconstrucciones.documents;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
@Component
@ConditionalOnProperty(name="ato.documents.worker-enabled",havingValue="true",matchIfMissing=true)
public class DocumentWorker {
    private final DocumentService documents;
    public DocumentWorker(DocumentService documents){this.documents=documents;}
    @Scheduled(fixedDelayString="${ato.documents.poll-ms:2000}") public void scan(){documents.scanNext();}
    @Scheduled(fixedDelay=60000) public void expire(){documents.expireReservations();}
}
