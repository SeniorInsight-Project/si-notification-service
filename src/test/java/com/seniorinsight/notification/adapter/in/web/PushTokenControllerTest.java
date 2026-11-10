package com.seniorinsight.notification.adapter.in.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import com.seniorinsight.notification.application.RegistrarTokenPushUseCase;
import com.seniorinsight.notification.application.RemoverTokenPushUseCase;
import com.seniorinsight.notification.application.TokenPushNaoEncontradoException;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Plataforma;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(value = PushTokenController.class,
        properties = "spring.security.oauth2.resourceserver.jwt.issuer-uri=http://localhost/realms/teste")
@Import({SegurancaConfig.class, TratadorDeErros.class})
class PushTokenControllerTest {

    private static final UUID USUARIO = UUID.randomUUID();
    private static final String CORPO = """
            {"token":"ExponentPushToken[abc]","identificadorAparelho":"pixel-7","plataforma":"ANDROID"}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    RegistrarTokenPushUseCase registrar;

    @MockitoBean
    RemoverTokenPushUseCase remover;

    @MockitoBean
    JwtDecoder jwtDecoder;

    @Test
    void registraOTokenDoUsuarioDoJwt() throws Exception {
        Instant agora = Instant.parse("2026-11-10T12:00:00Z");
        when(registrar.registrar(USUARIO, "pixel-7", "ExponentPushToken[abc]", Plataforma.ANDROID))
                .thenReturn(new DispositivoPush(USUARIO, "pixel-7", "ExponentPushToken[abc]", Plataforma.ANDROID, agora));

        mockMvc.perform(put("/v1/push-tokens").with(jwt().jwt(j -> j.subject(USUARIO.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.identificadorAparelho").value("pixel-7"))
                .andExpect(jsonPath("$.plataforma").value("ANDROID"))
                .andExpect(jsonPath("$.atualizadoEm").value("2026-11-10T12:00:00Z"))
                .andExpect(jsonPath("$.token").doesNotExist());
    }

    @Test
    void respondeSemTokenNaoAutenticado() throws Exception {
        mockMvc.perform(put("/v1/push-tokens").contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/v1/push-tokens/pixel-7")).andExpect(status().isUnauthorized());
    }

    @Test
    void respondeBadRequestParaPlataformaDesconhecida() throws Exception {
        mockMvc.perform(put("/v1/push-tokens").with(jwt().jwt(j -> j.subject(USUARIO.toString())))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"t\",\"identificadorAparelho\":\"a\",\"plataforma\":\"WEB\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.path").value("/v1/push-tokens"));
    }

    @Test
    void respondeBadRequestQuandoOUseCaseRejeitaOsDados() throws Exception {
        when(registrar.registrar(any(), any(), any(), any())).thenThrow(new IllegalArgumentException("token inválido"));

        mockMvc.perform(put("/v1/push-tokens").with(jwt().jwt(j -> j.subject(USUARIO.toString())))
                        .contentType(MediaType.APPLICATION_JSON).content(CORPO))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("token inválido"));
    }

    @Test
    void removeOTokenDoAparelho() throws Exception {
        mockMvc.perform(delete("/v1/push-tokens/pixel-7").with(jwt().jwt(j -> j.subject(USUARIO.toString()))))
                .andExpect(status().isNoContent());

        verify(remover).remover(USUARIO, "pixel-7");
    }

    @Test
    void removerAparelhoDesconhecidoRetornaNotFound() throws Exception {
        doThrow(new TokenPushNaoEncontradoException("x")).when(remover).remover(eq(USUARIO), eq("x"));

        mockMvc.perform(delete("/v1/push-tokens/x").with(jwt().jwt(j -> j.subject(USUARIO.toString()))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void rotasNaoMapeadasExigemAutenticacao() throws Exception {
        mockMvc.perform(get("/v1/qualquer-coisa")).andExpect(status().isUnauthorized());
    }
}
