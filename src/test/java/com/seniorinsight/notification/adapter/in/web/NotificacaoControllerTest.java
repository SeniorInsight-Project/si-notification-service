package com.seniorinsight.notification.adapter.in.web;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.application.ConfirmarLeituraNotificacaoUseCase;
import com.seniorinsight.notification.application.NotificacaoNaoEncontradaException;
import com.seniorinsight.notification.domain.model.LeituraNotificacao;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = NotificacaoController.class,
        properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/teste")
@Import({SegurancaConfig.class, TratadorDeErros.class})
class NotificacaoControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final UUID NOTIFICACAO = UUID.randomUUID();
    private static final String ROTA = "/v1/notificacoes/" + NOTIFICACAO + "/leitura";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ConfirmarLeituraNotificacaoUseCase confirmarLeitura;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void confirmaALeituraDoUsuarioDoJwt() throws Exception {
        when(confirmarLeitura.confirmar(USUARIO, NOTIFICACAO))
                .thenReturn(new LeituraNotificacao(NOTIFICACAO, Instant.parse("2026-11-11T12:00:00Z")));

        mockMvc.perform(put(ROTA).with(jwt().jwt(j -> j.subject(USUARIO.toString()))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.notificacaoId").value(NOTIFICACAO.toString()))
                .andExpect(jsonPath("$.lidaEm").value("2026-11-11T12:00:00Z"));

        verify(confirmarLeitura).confirmar(USUARIO, NOTIFICACAO);
    }

    @Test
    void respondeNaoAutenticadoSemToken() throws Exception {
        mockMvc.perform(put(ROTA)).andExpect(status().isUnauthorized());
    }

    @Test
    void respondeNotFoundQuandoNaoExisteOuNaoEDoUsuario() throws Exception {
        when(confirmarLeitura.confirmar(USUARIO, NOTIFICACAO)).thenThrow(new NotificacaoNaoEncontradaException());

        mockMvc.perform(put(ROTA).with(jwt().jwt(j -> j.subject(USUARIO.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.path").value(ROTA));
    }

    @Test
    void respondeBadRequestParaIdQueNaoEUuid() throws Exception {
        mockMvc.perform(put("/v1/notificacoes/abc/leitura").with(jwt().jwt(j -> j.subject(USUARIO.toString()))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }
}
