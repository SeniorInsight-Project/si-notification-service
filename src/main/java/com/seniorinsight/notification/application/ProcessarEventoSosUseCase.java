package com.seniorinsight.notification.application;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.EventoSos;
import com.seniorinsight.notification.domain.model.Localizacao;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;

public class ProcessarEventoSosUseCase {

    static final String TIPO = "SOS";
    static final String TITULO = "Alerta de emergência";
    static final String NOME_PADRAO = "Idoso";
    static final int TAMANHO_MAXIMO_NOME = 120;

    private final NotificacaoRepository notificacaoRepository;
    private final Clock clock;

    public ProcessarEventoSosUseCase(NotificacaoRepository notificacaoRepository, Clock clock) {
        this.notificacaoRepository = notificacaoRepository;
        this.clock = clock;
    }

    /**
     * Registra a notificação do SOS. Retorna {@code false} quando o evento já tinha sido registrado
     * (reentrega da mesma mensagem), sem criar uma notificação duplicada.
     */
    public boolean processar(EventoSos evento) {
        Instant ocorridoEm = evento.ocorridoEm() == null ? clock.instant() : evento.ocorridoEm();
        Notificacao notificacao = new Notificacao(
                UUID.randomUUID(),
                evento.idEvento(),
                TIPO,
                evento.idosoId(),
                TITULO,
                "O botão SOS foi acionado por " + nomeExibido(evento.nomeIdoso()) + ".",
                ocorridoEm,
                Localizacao.seValida(evento.latitude(), evento.longitude()).orElse(null),
                nivelBateriaValido(evento.nivelBateria()),
                observacaoNormalizada(evento.observacao())
        );
        return notificacaoRepository.registrarSeAusente(notificacao);
    }

    private static String nomeExibido(String nomeIdoso) {
        if (nomeIdoso == null || nomeIdoso.isBlank()) {
            return NOME_PADRAO;
        }
        return truncar(nomeIdoso.trim(), TAMANHO_MAXIMO_NOME);
    }

    private static Integer nivelBateriaValido(Integer nivelBateria) {
        return nivelBateria != null && nivelBateria >= 0 && nivelBateria <= 100 ? nivelBateria : null;
    }

    private static String observacaoNormalizada(String observacao) {
        if (observacao == null || observacao.isBlank()) {
            return null;
        }
        return truncar(observacao.trim(), Notificacao.TAMANHO_MAXIMO_OBSERVACAO);
    }

    private static String truncar(String texto, int tamanhoMaximo) {
        if (texto.length() <= tamanhoMaximo) {
            return texto;
        }
        String cortado = texto.substring(0, tamanhoMaximo - 1);
        // Não deixa meio par surrogate (ex.: emoji) no fim do texto cortado.
        if (Character.isHighSurrogate(cortado.charAt(cortado.length() - 1))) {
            cortado = cortado.substring(0, cortado.length() - 1);
        }
        return cortado + "…";
    }
}
