package com.daraya.atoconstrucciones.integrations.mail;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import jakarta.mail.internet.MimeMessage;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;
class GmailContactMailTests {
    @Test void gmailRequiresTlsAndUsesFixedRecipientMinimalBodyAndStableMessageId()throws Exception{
        var env=new MockEnvironment().withProperty("ato.mail.enabled","true").withProperty("ato.mail.username","sender@example.invalid").withProperty("ato.mail.password","synthetic-password").withProperty("ato.mail.recipient","recipient@example.invalid").withProperty("ato.web-origin","https://ato.example.invalid");
        class Capture extends JavaMailSenderImpl {MimeMessage received;@Override public void send(MimeMessage message){try{message.saveChanges();received=message;}catch(Exception e){throw new IllegalStateException(e);}}}
        var capture=new Capture();UUID reference=UUID.randomUUID();var adapter=new GmailContactMail(env,()->capture);adapter.notify(reference);
        assertThat(capture.getHost()).isEqualTo("smtp.gmail.com");assertThat(capture.getPort()).isEqualTo(587);var props=capture.getJavaMailProperties();assertThat(props.getProperty("mail.smtp.starttls.required")).isEqualTo("true");assertThat(props.getProperty("mail.smtp.ssl.checkserveridentity")).isEqualTo("true");assertThat(props.getProperty("mail.smtp.timeout")).isEqualTo("5000");
        assertThat(capture.received.getHeader("Message-ID")).containsExactly("<ato-contact-"+reference+"@atoconstrucciones.com>");assertThat(capture.received.getAllRecipients()).hasSize(1);assertThat(capture.received.getAllRecipients()[0].toString()).isEqualTo("recipient@example.invalid");
        String content=capture.received.getContent().toString();assertThat(content).contains(reference.toString(),"/publicacion#contactos").doesNotContain("synthetic-password","sender@example.invalid","visitor@example.invalid");assertThat(capture.received.getHeader("Reply-To")).isNull();
    }
    @Test void mailDisabledDoesNotConstructTransport(){var adapter=new GmailContactMail(new MockEnvironment(),()->{throw new AssertionError("transport must not be constructed");});assertThat(adapter.available()).isFalse();assertThatThrownBy(()->adapter.notify(UUID.randomUUID())).isInstanceOf(IllegalStateException.class);}
}
