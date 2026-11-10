package com.seniorinsight.notification.domain.port;

public interface EmailSender {

    /** @return {@code true} se o e-mail foi aceito para envio. */
    boolean enviar(String para, String assunto, String texto);
}
