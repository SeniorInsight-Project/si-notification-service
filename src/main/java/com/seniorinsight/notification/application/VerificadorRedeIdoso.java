package com.seniorinsight.notification.application;

import java.util.UUID;

import com.seniorinsight.notification.domain.port.RedeProtecaoClient;

/** Confere o vínculo do usuário com o idoso na rede de proteção do care-service (rede com cache no cliente). */
class VerificadorRedeIdoso {

    private final RedeProtecaoClient rede;

    VerificadorRedeIdoso(RedeProtecaoClient rede) {
        this.rede = rede;
    }

    void exigirMembro(UUID usuarioId, UUID idosoId) {
        boolean membro = rede.listarRede(idosoId).stream()
                .anyMatch(destinatario -> usuarioId.equals(destinatario.usuarioId()));
        if (!membro) {
            throw new AcessoNegadoException("O usuário não faz parte da rede do idoso.");
        }
    }
}
