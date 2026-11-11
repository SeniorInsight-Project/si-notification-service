package com.seniorinsight.notification.application;

import java.util.List;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;

public class ListarAlertasUseCase {

    private final AlertaRepository alertas;
    private final VerificadorRedeIdoso verificador;

    public ListarAlertasUseCase(AlertaRepository alertas, RedeProtecaoClient rede) {
        this.alertas = alertas;
        this.verificador = new VerificadorRedeIdoso(rede);
    }

    /** @throws AcessoNegadoException se o usuário não é da rede do idoso. */
    public List<Alerta> listar(UUID usuarioId, UUID idosoId) {
        verificador.exigirMembro(usuarioId, idosoId);
        return alertas.listarPorIdoso(idosoId);
    }
}
