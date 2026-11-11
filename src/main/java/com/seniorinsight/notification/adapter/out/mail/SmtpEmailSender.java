package com.seniorinsight.notification.adapter.out.mail;

import com.seniorinsight.notification.domain.port.EmailSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

/** E-mail por SMTP ({@code spring.mail.*}). Sem SMTP configurado, registra o aviso e devolve falha. */
public class SmtpEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

    private final ObjectProvider<JavaMailSender> mailSender;
    private final String remetente;

    public SmtpEmailSender(ObjectProvider<JavaMailSender> mailSender, String remetente) {
        this.mailSender = mailSender;
        this.remetente = remetente;
    }

    @Override
    public boolean enviar(String para, String assunto, String texto) {
        JavaMailSender sender = mailSender.getIfAvailable();
        if (sender == null) {
            log.warn("SMTP nao configurado (spring.mail.host); e-mail nao enviado.");
            return false;
        }
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(remetente);
        mensagem.setTo(para);
        mensagem.setSubject(assunto);
        mensagem.setText(texto);
        try {
            sender.send(mensagem);
            return true;
        } catch (MailException exception) {
            log.error("Falha ao enviar e-mail do SOS.", exception);
            return false;
        }
    }
}
