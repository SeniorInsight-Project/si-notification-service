package com.seniorinsight.notification.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.notification.application.AcessoNegadoException;
import com.seniorinsight.notification.application.AlertaNaoEncontradoException;
import com.seniorinsight.notification.application.AvaliarAlertaUseCase;
import com.seniorinsight.notification.application.ListarAlertasUseCase;
import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.AvaliacaoAlerta;
import com.seniorinsight.notification.domain.model.PrazoAvaliacaoVencidoException;
import com.seniorinsight.notification.domain.model.TipoAlerta;
import com.seniorinsight.notification.domain.port.RedeProtecaoIndisponivelException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

@WebMvcTest(value = AlertaController.class,
        properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/teste")
@Import({SegurancaConfig.class, TratadorDeErros.class})
class AlertaControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID IDOSO = UUID.randomUUID();
    private static final Instant GERADO = Instant.parse("2026-11-10T12:00:00Z");
    private static final String URL_LISTA = "/v1/idosos/" + IDOSO + "/alertas";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ListarAlertasUseCase listar;

    @MockitoBean
    AvaliarAlertaUseCase avaliar;

    @MockitoBean
    JwtDecoder jwtDecoder;

    private final Alerta alerta = Alerta.novo(UUID.randomUUID(), IDOSO, TipoAlerta.SOS, "Alerta de emergência", "SOS de Maria.", GERADO);

    private static RequestPostProcessor usuario() {
        return jwt().jwt(j -> j.subject(USUARIO.toString()));
    }

    private String urlAvaliacao() {
        return URL_LISTA + "/" + alerta.id() + "/avaliacao";
    }

    @Test
    void listaOsAlertasEmIso8601() throws Exception {
        when(listar.listar(USUARIO, IDOSO)).thenReturn(List.of(alerta));

        mockMvc.perform(get(URL_LISTA).with(usuario()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(alerta.id().toString()))
                .andExpect(jsonPath("$[0].idosoId").value(IDOSO.toString()))
                .andExpect(jsonPath("$[0].tipo").value("SOS"))
                .andExpect(jsonPath("$[0].titulo").value("Alerta de emergência"))
                .andExpect(jsonPath("$[0].geradoEm").value("2026-11-10T12:00:00Z"))
                .andExpect(jsonPath("$[0].avaliacao").doesNotExist());
    }

    @Test
    void listarSemTokenRetorna401() throws Exception {
        mockMvc.perform(get(URL_LISTA)).andExpect(status().isUnauthorized());
    }

    @Test
    void listarForaDaRedeRetorna403() throws Exception {
        when(listar.listar(USUARIO, IDOSO)).thenThrow(new AcessoNegadoException("fora da rede"));

        mockMvc.perform(get(URL_LISTA).with(usuario()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void listarComCareForaDoArRetorna503() throws Exception {
        when(listar.listar(USUARIO, IDOSO)).thenThrow(new RedeProtecaoIndisponivelException("fora", null));

        mockMvc.perform(get(URL_LISTA).with(usuario())).andExpect(status().isServiceUnavailable());
    }

    @Test
    void avaliaOAlerta() throws Exception {
        Alerta avaliado = alerta.avaliar(AvaliacaoAlerta.UTIL, "ok", GERADO.plusSeconds(60));
        when(avaliar.avaliar(USUARIO, IDOSO, alerta.id(), AvaliacaoAlerta.UTIL, "ok")).thenReturn(avaliado);

        mockMvc.perform(put(urlAvaliacao()).with(usuario()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avaliacao\":\"UTIL\",\"motivo\":\"ok\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avaliacao").value("UTIL"))
                .andExpect(jsonPath("$.avaliadoEm").value("2026-11-10T12:01:00Z"))
                .andExpect(jsonPath("$.motivoAvaliacao").value("ok"));
    }

    @Test
    void avaliacaoInvalidaRetorna400() throws Exception {
        mockMvc.perform(put(urlAvaliacao()).with(usuario()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avaliacao\":\"TALVEZ\"}"))
                .andExpect(status().isBadRequest());
        when(avaliar.avaliar(any(), any(), any(), eq(null), any())).thenThrow(new IllegalArgumentException("avaliacao deve ser UTIL ou FALSO."));
        mockMvc.perform(put(urlAvaliacao()).with(usuario()).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("avaliacao deve ser UTIL ou FALSO."));
    }

    @Test
    void prazoVencidoRetorna409() throws Exception {
        when(avaliar.avaliar(any(), any(), any(), any(), any())).thenThrow(new PrazoAvaliacaoVencidoException("vencido"));

        mockMvc.perform(put(urlAvaliacao()).with(usuario()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"avaliacao\":\"FALSO\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void alertaInexistenteRetorna404EForaDaRedeRetorna403() throws Exception {
        when(avaliar.avaliar(any(), any(), any(), any(), any()))
                .thenThrow(new AlertaNaoEncontradoException("x"))
                .thenThrow(new AcessoNegadoException("x"));
        String corpo = "{\"avaliacao\":\"UTIL\"}";

        mockMvc.perform(put(urlAvaliacao()).with(usuario()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isNotFound());
        mockMvc.perform(put(urlAvaliacao()).with(usuario()).contentType(MediaType.APPLICATION_JSON).content(corpo))
                .andExpect(status().isForbidden());
    }

    @Test
    void avaliarSemTokenRetorna401() throws Exception {
        mockMvc.perform(put(urlAvaliacao()).contentType(MediaType.APPLICATION_JSON).content("{\"avaliacao\":\"UTIL\"}"))
                .andExpect(status().isUnauthorized());
    }
}
