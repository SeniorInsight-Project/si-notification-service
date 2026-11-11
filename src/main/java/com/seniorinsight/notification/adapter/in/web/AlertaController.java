package com.seniorinsight.notification.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.notification.application.AvaliarAlertaUseCase;
import com.seniorinsight.notification.application.ListarAlertasUseCase;
import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.AvaliacaoAlerta;
import com.seniorinsight.notification.domain.model.TipoAlerta;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Central de alertas do contrato notification-v1 ({@code listarAlertas} e {@code avaliarAlerta}). */
@RestController
public class AlertaController {

    private final ListarAlertasUseCase listar;
    private final AvaliarAlertaUseCase avaliar;

    public AlertaController(ListarAlertasUseCase listar, AvaliarAlertaUseCase avaliar) {
        this.listar = listar;
        this.avaliar = avaliar;
    }

    @GetMapping("/v1/idosos/{idosoId}/alertas")
    public List<AlertaResponse> listar(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID idosoId) {
        return listar.listar(UsuarioDoJwt.id(jwt), idosoId).stream().map(AlertaResponse::de).toList();
    }

    @PutMapping("/v1/idosos/{idosoId}/alertas/{alertaId}/avaliacao")
    public AlertaResponse avaliar(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable UUID idosoId,
            @PathVariable UUID alertaId,
            @RequestBody AvaliarAlertaRequest request) {
        return AlertaResponse.de(avaliar.avaliar(
                UsuarioDoJwt.id(jwt), idosoId, alertaId, request.avaliacao(), request.motivo()));
    }

    public record AvaliarAlertaRequest(AvaliacaoAlerta avaliacao, String motivo) {
    }

    public record AlertaResponse(
            UUID id,
            UUID idosoId,
            TipoAlerta tipo,
            String titulo,
            String detalhe,
            Instant geradoEm,
            AvaliacaoAlerta avaliacao,
            Instant avaliadoEm,
            String motivoAvaliacao) {

        static AlertaResponse de(Alerta alerta) {
            return new AlertaResponse(alerta.id(), alerta.idosoId(), alerta.tipo(), alerta.titulo(), alerta.detalhe(),
                    alerta.geradoEm(), alerta.avaliacao(), alerta.avaliadoEm(), alerta.motivoAvaliacao());
        }
    }
}
