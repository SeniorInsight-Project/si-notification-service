package com.seniorinsight.notification.domain.port;

import java.util.List;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.DispositivoPush;

public interface DispositivoPushRepository {

    /** Insere ou atualiza o aparelho do usuário (chave: usuário + identificador do aparelho). */
    DispositivoPush salvar(DispositivoPush dispositivo);

    /** @return {@code true} se havia um token para o aparelho. */
    boolean remover(UUID usuarioId, String identificadorAparelho);

    List<DispositivoPush> listarAtivos(UUID usuarioId);

    void desativarPorToken(String token);

    /** Apaga todos os tokens do usuário, ativos ou não. */
    void removerTodos(UUID usuarioId);
}
