package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.AvaliacaoAlerta;
import com.seniorinsight.notification.domain.model.Destinatario;
import com.seniorinsight.notification.domain.model.PrazoAvaliacaoVencidoException;
import com.seniorinsight.notification.domain.model.TipoAlerta;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import com.seniorinsight.notification.domain.port.RedeProtecaoClient;
import com.seniorinsight.notification.domain.port.RedeProtecaoIndisponivelException;
import org.junit.jupiter.api.Test;

class AlertasUseCasesTest {

    private static final Instant GERADO = Instant.parse("2026-11-10T12:00:00Z");

    private final AlertaRepository alertas = mock(AlertaRepository.class);
    private final RedeProtecaoClient rede = mock(RedeProtecaoClient.class);

    private final UUID idosoId = UUID.randomUUID();
    private final UUID familiar = UUID.randomUUID();
    private final Alerta alerta = Alerta.novo(UUID.randomUUID(), idosoId, TipoAlerta.SOS, "SOS", "detalhe", GERADO);

    private ListarAlertasUseCase listar() {
        return new ListarAlertasUseCase(alertas, rede);
    }

    private AvaliarAlertaUseCase avaliar(Instant agora) {
        return new AvaliarAlertaUseCase(alertas, rede, Clock.fixed(agora, ZoneOffset.UTC));
    }

    private void familiarNaRede() {
        when(rede.listarRede(idosoId)).thenReturn(List.of(
                new Destinatario(familiar, "Ana", null), new Destinatario(null, "Sem conta", null)));
    }

    @Test
    void listaOsAlertasParaQuemEDaRede() {
        familiarNaRede();
        when(alertas.listarPorIdoso(idosoId)).thenReturn(List.of(alerta));

        assertEquals(List.of(alerta), listar().listar(familiar, idosoId));
    }

    @Test
    void naoListaParaQuemNaoEDaRede() {
        familiarNaRede();

        assertThrows(AcessoNegadoException.class, () -> listar().listar(UUID.randomUUID(), idosoId));
        verify(alertas, never()).listarPorIdoso(any());
    }

    @Test
    void propagaIndisponibilidadeDoCare() {
        when(rede.listarRede(idosoId)).thenThrow(new RedeProtecaoIndisponivelException("fora", null));

        assertThrows(RedeProtecaoIndisponivelException.class, () -> listar().listar(familiar, idosoId));
    }

    @Test
    void avaliaESalvaDentroDe24Horas() {
        familiarNaRede();
        when(alertas.buscarPorId(idosoId, alerta.id())).thenReturn(Optional.of(alerta));

        Alerta resultado = avaliar(GERADO.plusSeconds(120))
                .avaliar(familiar, idosoId, alerta.id(), AvaliacaoAlerta.FALSO, "foi engano");

        assertEquals(AvaliacaoAlerta.FALSO, resultado.avaliacao());
        verify(alertas).salvarAvaliacao(resultado);
    }

    @Test
    void retornaConflitoDepoisDe24Horas() {
        familiarNaRede();
        when(alertas.buscarPorId(idosoId, alerta.id())).thenReturn(Optional.of(alerta));
        Instant vencido = GERADO.plus(Alerta.PRAZO_AVALIACAO).plusSeconds(1);

        assertThrows(PrazoAvaliacaoVencidoException.class,
                () -> avaliar(vencido).avaliar(familiar, idosoId, alerta.id(), AvaliacaoAlerta.UTIL, null));
        verify(alertas, never()).salvarAvaliacao(any());
    }

    @Test
    void naoAvaliaSemSerDaRedeNemAlertaInexistente() {
        familiarNaRede();
        assertThrows(AcessoNegadoException.class,
                () -> avaliar(GERADO).avaliar(UUID.randomUUID(), idosoId, alerta.id(), AvaliacaoAlerta.UTIL, null));

        when(alertas.buscarPorId(idosoId, alerta.id())).thenReturn(Optional.empty());
        assertThrows(AlertaNaoEncontradoException.class,
                () -> avaliar(GERADO).avaliar(familiar, idosoId, alerta.id(), AvaliacaoAlerta.UTIL, null));
    }
}
