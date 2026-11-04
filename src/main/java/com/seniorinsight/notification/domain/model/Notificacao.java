package com.seniorinsight.notification.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Notificação a ser entregue aos familiares. Os limites de tamanho espelham as colunas da tabela
 * notification.notificacao, para que um texto grande demais falhe aqui e não no banco.
 */
public record Notificacao(
        UUID id,
        UUID idEvento,
        String tipo,
        UUID idosoId,
        String titulo,
        String mensagem,
        Instant ocorridoEm,
        Localizacao localizacao,
        Integer nivelBateria,
        String observacao
) {

    public static final int TAMANHO_MAXIMO_TIPO = 40;
    public static final int TAMANHO_MAXIMO_TITULO = 160;
    public static final int TAMANHO_MAXIMO_MENSAGEM = 1000;
    public static final int TAMANHO_MAXIMO_OBSERVACAO = 500;

    public Notificacao {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(idEvento, "idEvento");
        Objects.requireNonNull(idosoId, "idosoId");
        Objects.requireNonNull(ocorridoEm, "ocorridoEm");
        exigirTexto(tipo, "tipo", TAMANHO_MAXIMO_TIPO);
        exigirTexto(titulo, "titulo", TAMANHO_MAXIMO_TITULO);
        exigirTexto(mensagem, "mensagem", TAMANHO_MAXIMO_MENSAGEM);
        if (observacao != null && observacao.length() > TAMANHO_MAXIMO_OBSERVACAO) {
            throw new IllegalArgumentException(
                    "A observação excede " + TAMANHO_MAXIMO_OBSERVACAO + " caracteres.");
        }
        if (nivelBateria != null && (nivelBateria < 0 || nivelBateria > 100)) {
            throw new IllegalArgumentException("O nível de bateria deve estar entre 0 e 100.");
        }
    }

    private static void exigirTexto(String valor, String campo, int tamanhoMaximo) {
        if (valor == null || valor.isBlank()) {
            throw new IllegalArgumentException("O campo " + campo + " é obrigatório.");
        }
        if (valor.length() > tamanhoMaximo) {
            throw new IllegalArgumentException(
                    "O campo " + campo + " excede " + tamanhoMaximo + " caracteres.");
        }
    }
}
