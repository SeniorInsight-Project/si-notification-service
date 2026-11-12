package com.seniorinsight.notification.adapter.in.rabbit;

/** Envelope CloudEvents fora do contrato do si-contracts: repetir a entrega não resolve. */
public class EnvelopeInvalidoException extends RuntimeException {

    public EnvelopeInvalidoException(String motivo) {
        super(motivo);
    }
}
