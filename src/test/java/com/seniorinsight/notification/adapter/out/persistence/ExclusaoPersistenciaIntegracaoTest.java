package com.seniorinsight.notification.adapter.out.persistence;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.seniorinsight.notification.ConfiguracaoDeIntegracao;
import com.seniorinsight.notification.PostgresIntegracaoTest;
import com.seniorinsight.notification.domain.model.Alerta;
import com.seniorinsight.notification.domain.model.CanalEntrega;
import com.seniorinsight.notification.domain.model.DispositivoPush;
import com.seniorinsight.notification.domain.model.Entrega;
import com.seniorinsight.notification.domain.model.Notificacao;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.model.ProtocoloExclusao;
import com.seniorinsight.notification.domain.model.TipoAlerta;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Roda as migrações e o SQL da exclusão num PostgreSQL de verdade. Cada teste faz commit; a limpeza é no início. */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({DispositivoPushRepositoryAdapter.class, EntregaRepositoryAdapter.class, NotificacaoRepositoryAdapter.class,
        AlertaRepositoryAdapter.class, ProtocoloExclusaoRepositoryAdapter.class, TitularExcluidoRepositoryAdapter.class,
        OutboxPublicadorAdapter.class, TransacaoSpring.class, ConfiguracaoDeIntegracao.class})
class ExclusaoPersistenciaIntegracaoTest extends PostgresIntegracaoTest {

    @Autowired JdbcClient jdbc;
    @Autowired DispositivoPushRepositoryAdapter dispositivos;
    @Autowired EntregaRepositoryAdapter entregas;
    @Autowired NotificacaoRepositoryAdapter notificacoes;
    @Autowired AlertaRepositoryAdapter alertas;
    @Autowired ProtocoloExclusaoRepositoryAdapter protocolos;
    @Autowired TitularExcluidoRepositoryAdapter titulares;
    @Autowired OutboxPublicadorAdapter publicador;
    @Autowired TransacaoSpring transacao;

    private final UUID titular = UUID.randomUUID();
    private final UUID outro = UUID.randomUUID();
    private final UUID idoso = UUID.randomUUID();
    private final UUID outroIdoso = UUID.randomUUID();

    @BeforeEach
    void limpar() {
        jdbc.sql("TRUNCATE notification.notificacao, notification.dispositivo_push, notification.alerta, "
                + "notification.exclusao_protocolo, notification.titular_excluido, notification.outbox_evento CASCADE").update();
    }

    private DispositivoPush aparelho(UUID usuario, String nome) {
        return new DispositivoPush(usuario, nome, "ExponentPushToken[" + usuario + nome + "]", Plataforma.ANDROID, Instant.now());
    }

    private Notificacao notificacao(UUID idosoId) {
        Notificacao notificacao = new Notificacao(UUID.randomUUID(), UUID.randomUUID(), "SOS", idosoId,
                "Alerta de emergência", "SOS.", Instant.parse("2026-11-10T12:00:00Z"));
        notificacoes.registrarSeAusente(notificacao);
        alertas.registrarSeAusente(Alerta.novo(notificacao.idEvento(), idosoId, TipoAlerta.SOS, "t", "d", notificacao.ocorridoEm()));
        return notificacao;
    }

    private long contar(String tabela, String coluna, UUID valor) {
        return jdbc.sql("SELECT count(*) FROM notification." + tabela + " WHERE " + coluna + " = :v")
                .param("v", valor).query(Long.class).single();
    }

    @Test
    void apagaTokensETodasAsEntregasDoTitularSemTocarNosOutros() {
        dispositivos.salvar(aparelho(titular, "a"));
        dispositivos.salvar(aparelho(titular, "b"));
        dispositivos.desativarPorToken(aparelho(titular, "b").token());
        dispositivos.salvar(aparelho(outro, "a"));
        Notificacao n1 = notificacao(idoso);
        Notificacao n2 = notificacao(outroIdoso);
        for (Notificacao n : List.of(n1, n2)) {
            entregas.registrarSeAusente(Entrega.pendente(n.id(), titular, CanalEntrega.PUSH));
            entregas.registrarSeAusente(Entrega.pendente(n.id(), titular, CanalEntrega.EMAIL));
            entregas.registrarSeAusente(Entrega.pendente(n.id(), outro, CanalEntrega.PUSH));
        }

        dispositivos.removerTodos(titular);
        entregas.removerDoDestinatario(titular);

        assertEquals(0, contar("dispositivo_push", "usuario_id", titular));
        assertEquals(1, contar("dispositivo_push", "usuario_id", outro));
        assertEquals(0, contar("entrega", "destinatario_id", titular));
        assertEquals(2, contar("entrega", "destinatario_id", outro));
        assertEquals(2, contar("notificacao", "idoso_id", idoso) + contar("notificacao", "idoso_id", outroIdoso));
    }

    @Test
    void apagaNotificacoesEAlertasDoIdosoComAsEntregasEmCascata() {
        Notificacao doIdoso = notificacao(idoso);
        Notificacao deOutro = notificacao(outroIdoso);
        entregas.registrarSeAusente(Entrega.pendente(doIdoso.id(), outro, CanalEntrega.PUSH));
        entregas.registrarSeAusente(Entrega.pendente(deOutro.id(), outro, CanalEntrega.PUSH));

        notificacoes.removerDoIdoso(idoso);
        alertas.removerDoIdoso(idoso);

        assertEquals(0, contar("notificacao", "idoso_id", idoso));
        assertEquals(0, contar("alerta", "idoso_id", idoso));
        assertEquals(0, contar("entrega", "notificacao_id", doIdoso.id()));
        assertEquals(1, contar("notificacao", "idoso_id", outroIdoso));
        assertEquals(1, contar("alerta", "idoso_id", outroIdoso));
        assertEquals(1, contar("entrega", "notificacao_id", deOutro.id()));
    }

    @Test
    void guardaSoOHashDoTitularExcluido() {
        assertFalse(titulares.existe(titular));

        transacao.executar(() -> {
            titulares.travar(titular);
            titulares.registrar(titular);
            titulares.registrar(titular);
        });

        assertTrue(titulares.existe(titular));
        assertFalse(titulares.existe(outro));
        String guardado = jdbc.sql("SELECT usuario_hash FROM notification.titular_excluido").query(String.class).single();
        assertEquals(64, guardado.length());
        assertNotEquals(titular.toString(), guardado);
    }

    @Test
    void acompanhaOAndamentoDoProtocolo() {
        UUID protocolo = UUID.randomUUID();

        assertEquals(new ProtocoloExclusao(protocolo, false, false), protocolos.registrar(protocolo));
        protocolos.marcarAvisoEnviado(protocolo);
        assertEquals(new ProtocoloExclusao(protocolo, true, false), protocolos.registrar(protocolo));
        assertTrue(protocolos.concluir(protocolo));
        assertFalse(protocolos.concluir(protocolo));
        assertEquals(new ProtocoloExclusao(protocolo, true, true), protocolos.registrar(protocolo));
    }

    @Test
    void gravaAConfirmacaoNaOutboxComOEnvelopeDoContrato() throws Exception {
        UUID protocolo = UUID.randomUUID();

        transacao.executar(() -> publicador.publicar(protocolo));

        JsonNode envelope = new ObjectMapper().readTree(
                jdbc.sql("SELECT payload::text FROM notification.outbox_evento WHERE tipo_evento = 'lgpd.erasure.completed'")
                        .query(String.class).single());
        assertEquals("1.0", envelope.get("specversion").asText());
        assertEquals("/notification-service", envelope.get("source").asText());
        assertEquals("lgpd.erasure.completed", envelope.get("type").asText());
        assertEquals(ConfiguracaoDeIntegracao.AGORA.toString(), envelope.get("time").asText());
        assertEquals("application/json", envelope.get("datacontenttype").asText());
        assertEquals("https://raw.githubusercontent.com/SeniorInsight-Project/si-contracts/main/events/lgpd.erasure.completed.v1.json",
                envelope.get("dataschema").asText());
        assertEquals(protocolo.toString(), envelope.get("subject").asText());
        assertEquals(protocolo.toString(), envelope.get("data").get("protocolo").asText());
        assertEquals("notification-service", envelope.get("data").get("servico").asText());
        UUID idDoEnvelope = UUID.fromString(envelope.get("id").asText());
        assertEquals(1, contar("outbox_evento", "id", idDoEnvelope));
    }

    @Test
    void aConfirmacaoESoGravadaSeATransacaoDaExclusaoConfirmar() {
        UUID protocolo = UUID.randomUUID();

        assertThrows(IllegalStateException.class, () -> transacao.executar(() -> {
            titulares.registrar(titular);
            publicador.publicar(protocolo);
            throw new IllegalStateException("banco caiu");
        }));

        assertEquals(0, contar("outbox_evento", "agregado_id", protocolo));
        assertFalse(titulares.existe(titular));
    }
}
