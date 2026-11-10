package com.seniorinsight.notification.application;

import java.time.Clock;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.AvaliacaoAlerta;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;

public class AvaliarAlertaUseCase {

    private final AlertaRepository alertas;
    private final VerificadorRedeIdoso verificador;
    private final Clock clock;

    public AvaliarAlertaUseCase(AlertaRepository alertas, RedeProtecaoClient rede, Clock clock) {
        this.alertas = alertas;
        this.verificador = new VerificadorRedeIdoso(rede);
        this.clock = clock;
    }

    /**
     * @throws AcessoNegadoException se o usuário não é da rede do idoso.
     * @throws AlertaNaoEncontradoException se o alerta não existe para o idoso.
     * @throws com.seniorinsight.notification.domain.model.PrazoAvaliacaoVencidoException depois de 24 h.
     */
    public Alerta avaliar(UUID usuarioId, UUID idosoId, UUID alertaId, AvaliacaoAlerta avaliacao, String motivo) {
        verificador.exigirMembro(usuarioId, idosoId);
        Alerta alerta = alertas.buscarPorId(idosoId, alertaId)
                .orElseThrow(() -> new AlertaNaoEncontradoException("Alerta " + alertaId + " não encontrado."));
        Alerta avaliado = alerta.avaliar(avaliacao, motivo, clock.instant());
        alertas.salvarAvaliacao(avaliado);
        return avaliado;
    }
}
