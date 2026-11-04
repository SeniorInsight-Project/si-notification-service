package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.seniorinsight.notification.domain.model.EventoSos;
import com.seniorinsight.notification.domain.model.Localizacao;
import com.seniorinsight.notification.domain.model.Notificacao;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class ProcessarEventoSosUseCaseTest {

    private static final Instant AGORA = Instant.parse("2026-11-04T15:30:00Z");

    private final List<Notificacao> registradas = new ArrayList<>();
    private boolean respostaDoRepositorio = true;
    private final ProcessarEventoSosUseCase useCase = new ProcessarEventoSosUseCase(
            notificacao -> {
                registradas.add(notificacao);
                return respostaDoRepositorio;
            },
            Clock.fixed(AGORA, ZoneOffset.UTC));

    @Test
    void montaNotificacaoDeSosComOsDadosDoEvento() {
        EventoSos evento = new EventoSos(UUID.randomUUID(), UUID.randomUUID(), "  Maria  ",
                -23.55, -46.63, 42, "  caí na cozinha ", Instant.parse("2026-10-26T12:00:00Z"));

        assertTrue(useCase.processar(evento));

        Notificacao notificacao = unicaRegistrada();
        assertEquals(evento.idEvento(), notificacao.idEvento());
        assertEquals(evento.idosoId(), notificacao.idosoId());
        assertEquals("SOS", notificacao.tipo());
        assertEquals("Alerta de emergência", notificacao.titulo());
        assertEquals("O botão SOS foi acionado por Maria.", notificacao.mensagem());
        assertEquals(evento.ocorridoEm(), notificacao.ocorridoEm());
        assertEquals(new Localizacao(-23.55, -46.63), notificacao.localizacao());
        assertEquals(42, notificacao.nivelBateria());
        assertEquals("caí na cozinha", notificacao.observacao());
    }

    @Test
    void repassaQueOEventoJaTinhaSidoRegistrado() {
        respostaDoRepositorio = false;

        assertFalse(useCase.processar(eventoMinimo()));
    }

    @Test
    void usaNomePadraoQuandoNomeAusenteOuEmBranco() {
        useCase.processar(eventoComNome(null));
        useCase.processar(eventoComNome("   "));

        assertEquals("O botão SOS foi acionado por Idoso.", registradas.get(0).mensagem());
        assertEquals("O botão SOS foi acionado por Idoso.", registradas.get(1).mensagem());
    }

    @Test
    void truncaNomeLongoParaCaberNaMensagem() {
        useCase.processar(eventoComNome("A".repeat(5_000)));

        String mensagem = unicaRegistrada().mensagem();
        assertTrue(mensagem.length() <= Notificacao.TAMANHO_MAXIMO_MENSAGEM);
        assertTrue(mensagem.endsWith("…."));
        assertEquals("O botão SOS foi acionado por ".length() + ProcessarEventoSosUseCase.TAMANHO_MAXIMO_NOME + 1,
                mensagem.length());
    }

    @Test
    void usaHorarioAtualQuandoEventoNaoInformaQuandoOcorreu() {
        useCase.processar(eventoMinimo());

        assertEquals(AGORA, unicaRegistrada().ocorridoEm());
    }

    @Test
    void descartaContextoInvalidoSemRejeitarOSos() {
        EventoSos evento = new EventoSos(UUID.randomUUID(), UUID.randomUUID(), "Maria",
                95.0, -46.63, 150, "   ", null);

        assertTrue(useCase.processar(evento));

        Notificacao notificacao = unicaRegistrada();
        assertNull(notificacao.localizacao());
        assertNull(notificacao.nivelBateria());
        assertNull(notificacao.observacao());
    }

    @Test
    void descartaLocalizacaoIncompleta() {
        useCase.processar(new EventoSos(UUID.randomUUID(), UUID.randomUUID(), "Maria",
                -23.55, null, null, null, null));

        assertNull(unicaRegistrada().localizacao());
    }

    @Test
    void truncaObservacaoLonga() {
        useCase.processar(new EventoSos(UUID.randomUUID(), UUID.randomUUID(), "Maria",
                null, null, null, "x".repeat(2_000), null));

        assertEquals(Notificacao.TAMANHO_MAXIMO_OBSERVACAO, unicaRegistrada().observacao().length());
    }

    @Test
    void mantemNomeNoLimiteSemTruncar() {
        String nome = "B".repeat(ProcessarEventoSosUseCase.TAMANHO_MAXIMO_NOME);

        useCase.processar(eventoComNome(nome));

        assertEquals("O botão SOS foi acionado por " + nome + ".", unicaRegistrada().mensagem());
    }

    @Test
    void naoCortaEmojiAoMeioAoTruncarNome() {
        // 118 letras + emoji (2 chars UTF-16): o corte em 119 cairia no meio do emoji.
        String nome = "C".repeat(118) + "😀" + "D".repeat(10);

        useCase.processar(eventoComNome(nome));

        String mensagem = unicaRegistrada().mensagem();
        assertEquals("O botão SOS foi acionado por " + "C".repeat(118) + "….", mensagem);
        assertTrue(mensagem.chars().noneMatch(c -> Character.isSurrogate((char) c)), "sobrou meio emoji na mensagem");
    }

    @Test
    void mantemObservacaoNoLimiteSemTruncar() {
        String observacao = "o".repeat(Notificacao.TAMANHO_MAXIMO_OBSERVACAO);

        useCase.processar(new EventoSos(UUID.randomUUID(), UUID.randomUUID(), "Maria",
                null, null, null, observacao, null));

        assertEquals(observacao, unicaRegistrada().observacao());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 50, 99, 100})
    void mantemNivelDeBateriaValido(int nivel) {
        useCase.processar(new EventoSos(UUID.randomUUID(), UUID.randomUUID(), "Maria",
                null, null, nivel, null, null));

        assertEquals(nivel, unicaRegistrada().nivelBateria());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 101, Integer.MIN_VALUE, Integer.MAX_VALUE})
    void descartaNivelDeBateriaForaDaFaixa(int nivel) {
        useCase.processar(new EventoSos(UUID.randomUUID(), UUID.randomUUID(), "Maria",
                null, null, nivel, null, null));

        assertNull(unicaRegistrada().nivelBateria());
    }

    @ParameterizedTest
    @CsvSource({"90, 180", "-90, -180", "0, 0", "-23.5505, -46.6333"})
    void mantemCoordenadasValidasInclusiveNosLimites(double latitude, double longitude) {
        useCase.processar(new EventoSos(UUID.randomUUID(), UUID.randomUUID(), "Maria",
                latitude, longitude, null, null, null));

        assertEquals(new Localizacao(latitude, longitude), unicaRegistrada().localizacao());
    }

    @ParameterizedTest
    @CsvSource({"90.0001, 0", "-90.0001, 0", "0, 180.0001", "0, -180.0001",
            "NaN, 0", "0, NaN", "Infinity, 0", "0, -Infinity"})
    void descartaCoordenadasInvalidas(double latitude, double longitude) {
        useCase.processar(new EventoSos(UUID.randomUUID(), UUID.randomUUID(), "Maria",
                latitude, longitude, null, null, null));

        assertNull(unicaRegistrada().localizacao());
    }

    @Test
    void geraIdentificadorProprioParaCadaNotificacao() {
        useCase.processar(eventoMinimo());
        useCase.processar(eventoMinimo());

        assertEquals(2, registradas.stream().map(Notificacao::id).distinct().count());
    }

    @Test
    void propagaFalhaDoRepositorioParaOAdapterDecidir() {
        RuntimeException falha = new IllegalStateException("banco");
        ProcessarEventoSosUseCase comFalha = new ProcessarEventoSosUseCase(
                notificacao -> {
                    throw falha;
                },
                Clock.fixed(AGORA, ZoneOffset.UTC));

        assertSame(falha, assertThrows(IllegalStateException.class, () -> comFalha.processar(eventoMinimo())));
    }

    private Notificacao unicaRegistrada() {
        assertEquals(1, registradas.size());
        return registradas.get(0);
    }

    private static EventoSos eventoMinimo() {
        return eventoComNome("Maria");
    }

    private static EventoSos eventoComNome(String nome) {
        return new EventoSos(UUID.randomUUID(), UUID.randomUUID(), nome, null, null, null, null, null);
    }
}
