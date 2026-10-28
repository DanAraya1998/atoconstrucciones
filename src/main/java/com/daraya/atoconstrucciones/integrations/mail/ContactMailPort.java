package com.daraya.atoconstrucciones.integrations.mail;
import java.util.UUID;
public interface ContactMailPort {
    boolean available();
    /** SMTP es at-least-once: Message-ID estable ayuda a correlacionar, no garantiza deduplicación. */
    void notify(UUID reference);
}
