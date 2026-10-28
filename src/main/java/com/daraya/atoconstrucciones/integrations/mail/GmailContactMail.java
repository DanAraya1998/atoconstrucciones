package com.daraya.atoconstrucciones.integrations.mail;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;
import java.util.UUID;
@Component
public class GmailContactMail implements ContactMailPort {
    private final Environment env;private final java.util.function.Supplier<JavaMailSenderImpl> factory;
    @org.springframework.beans.factory.annotation.Autowired public GmailContactMail(Environment env){this(env,JavaMailSenderImpl::new);}
    GmailContactMail(Environment env,java.util.function.Supplier<JavaMailSenderImpl> factory){this.env=env;this.factory=factory;}
    public boolean available(){return env.getProperty("ato.mail.enabled",Boolean.class,false);}
    public void notify(UUID reference){
        if(!available())throw new IllegalStateException("MAIL_NOT_CONFIGURED");
        var sender=factory.get();sender.setHost("smtp.gmail.com");sender.setPort(587);
        sender.setUsername(env.getRequiredProperty("ato.mail.username"));sender.setPassword(env.getRequiredProperty("ato.mail.password"));
        var props=sender.getJavaMailProperties();props.setProperty("mail.smtp.auth","true");props.setProperty("mail.smtp.starttls.enable","true");props.setProperty("mail.smtp.starttls.required","true");props.setProperty("mail.smtp.ssl.checkserveridentity","true");
        props.setProperty("mail.smtp.connectiontimeout","5000");props.setProperty("mail.smtp.timeout","5000");props.setProperty("mail.smtp.writetimeout","5000");
        try {
            var message=sender.createMimeMessage();message.setFrom(env.getRequiredProperty("ato.mail.username"));message.setRecipients(jakarta.mail.Message.RecipientType.TO,env.getRequiredProperty("ato.mail.recipient"));
            message.setSubject("Nuevo contacto de ATO Construcciones","UTF-8");
            message.setText("Se registró una solicitud de contacto. Referencia: "+reference+"\nRevise el espacio privado: "+env.getRequiredProperty("ato.web-origin")+"/publicacion#contactos\nEsta notificación no acredita lectura ni respuesta.","UTF-8");
            // JavaMail puede regenerar Message-ID; override lo conserva al enviar.
            var stable=new jakarta.mail.internet.MimeMessage(message){@Override protected void updateMessageID()throws jakarta.mail.MessagingException{setHeader("Message-ID","<ato-contact-"+reference+"@atoconstrucciones.com>");}};
            sender.send(stable);
        }catch(jakarta.mail.MessagingException e){throw new IllegalStateException("MAIL_SEND_FAILED");}
    }
}
