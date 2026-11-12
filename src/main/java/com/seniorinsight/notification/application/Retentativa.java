package com.seniorinsight.notification.application;

import java.time.Duration;
import java.util.Optional;
import java.util.function.Supplier;

/** Repete uma ação com intervalo fixo, até um limite de tentativas. */
public class Retentativa {

    private final int tentativas;
    private final Duration intervalo;
    private final Pausa pausa;

    public Retentativa(int tentativas, Duration intervalo, Pausa pausa) {
        if (tentativas < 1) {
            throw new IllegalArgumentException("É preciso ao menos uma tentativa.");
        }
        this.tentativas = tentativas;
        this.intervalo = intervalo;
        this.pausa = pausa;
    }

    /** Repete enquanto a ação lançar exceção; no limite, propaga a da última tentativa. */
    public void executar(Runnable acao) {
        buscar(() -> {
            acao.run();
            return Optional.of(Boolean.TRUE);
        });
    }

    /**
     * Repete enquanto a busca vier vazia ou lançar exceção. No limite, devolve vazio, ou propaga a exceção se foi ela
     * que encerrou a última tentativa.
     */
    public <T> Optional<T> buscar(Supplier<Optional<T>> busca) {
        for (int tentativa = 1; tentativa < tentativas; tentativa++) {
            try {
                Optional<T> resultado = busca.get();
                if (resultado.isPresent()) {
                    return resultado;
                }
            } catch (RuntimeException exception) {
                // Falha temporária: tenta de novo depois da pausa.
            }
            pausa.aguardar(intervalo);
        }
        return busca.get();
    }
}
