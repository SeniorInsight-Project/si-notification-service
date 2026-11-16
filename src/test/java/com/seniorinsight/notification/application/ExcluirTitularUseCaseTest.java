package com.seniorinsight.notification.application;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import com.seniorinsight.notification.domain.model.PedidoExclusao;
import com.seniorinsight.notification.domain.model.Plataforma;
import com.seniorinsight.notification.domain.model.ProtocoloExclusao;
import com.seniorinsight.notification.domain.model.TipoTitular;
import com.seniorinsight.notification.domain.port.AlertaRepository;
import com.seniorinsight.notification.domain.port.DispositivoPushRepository;
import com.seniorinsight.notification.domain.port.EntregaRepository;
import com.seniorinsight.notification.domain.port.NotificacaoRepository;
import com.seniorinsight.notification.domain.port.ProtocoloExclusaoRepository;
import com.seniorinsight.notification.domain.port.PublicadorExclusaoConcluida;
import com.seniorinsight.notification.domain.port.TitularExcluidoRepository;
import com.seniorinsight.notification.domain.port.Transacao;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

class ExcluirTitularUseCaseTest {

    private final UUID protocolo = UUID.randomUUID();
    private final UUID titularId = UUID.randomUUID();
    private final UUID titularSub = UUID.randomUUID();

    private final ProtocolosEmMemoria protocolos = new ProtocolosEmMemoria();
    private final TitularesEmMemoria titulares = new TitularesEmMemoria();
    private final AvisarRedeExclusaoUseCase avisarRede = mock(AvisarRedeExclusaoUseCase.class);
    private final DispositivoPushRepository dispositivos = mock(DispositivoPushRepository.class);
    private final EntregaRepository entregas = mock(EntregaRepository.class);
    private final NotificacaoRepository notificacoes = mock(NotificacaoRepository.class);
    private final AlertaRepository alertas = mock(AlertaRepository.class);
    private final PublicadorExclusaoConcluida publicador = mock(PublicadorExclusaoConcluida.class);
    private final Transacao transacao = new Transacao() {
        @Override
        public <T> T obter(Supplier<T> acao) {
            Map<UUID, ProtocoloExclusao> antes = new HashMap<>(protocolos.estado);
            Set<UUID> excluidosAntes = new HashSet<>(titulares.excluidos);
            try {
                return acao.get();
            } catch (RuntimeException exception) {
                protocolos.estado.clear();
                protocolos.estado.putAll(antes);
                titulares.excluidos.clear();
                titulares.excluidos.addAll(excluidosAntes);
                throw exception;
            }
        }
    };
    private final ExcluirTitularUseCase useCase = new ExcluirTitularUseCase(
            protocolos, titulares, avisarRede, transacao, dispositivos, entregas, notificacoes, alertas, publicador);

    private PedidoExclusao pedido(TipoTitular tipo) {
        return new PedidoExclusao(protocolo, tipo, titularId, titularSub);
    }

    @Test
    void idosoAvisaARedeEDepoisApagaTudoEConfirmaUmaVez() {
        useCase.executar(pedido(TipoTitular.IDOSO));

        InOrder ordem = inOrder(avisarRede, dispositivos, entregas, notificacoes, alertas, publicador);
        ordem.verify(avisarRede).avisar(protocolo);
        ordem.verify(dispositivos).removerTodos(titularSub);
        ordem.verify(entregas).removerDoDestinatario(titularSub);
        ordem.verify(notificacoes).removerDoIdoso(titularId);
        ordem.verify(alertas).removerDoIdoso(titularId);
        ordem.verify(publicador).publicar(protocolo);
        verify(publicador, times(1)).publicar(protocolo);
        assertTrue(titulares.existe(titularSub));
    }

    @Test
    void familiarApagaSoOsTokensEAsEntregasDeleESemAviso() {
        useCase.executar(pedido(TipoTitular.FAMILIAR));

        verifyNoInteractions(avisarRede, notificacoes, alertas);
        verify(dispositivos).removerTodos(titularSub);
        verify(entregas).removerDoDestinatario(titularSub);
        verify(publicador).publicar(protocolo);
        assertTrue(titulares.existe(titularSub));
    }

    @Test
    void reentregaDoMesmoProtocoloNaoRepeteAvisoNemConfirmacao() {
        useCase.executar(pedido(TipoTitular.IDOSO));
        useCase.executar(pedido(TipoTitular.IDOSO));

        verify(avisarRede, times(1)).avisar(protocolo);
        verify(dispositivos, times(1)).removerTodos(titularSub);
        verify(publicador, times(1)).publicar(protocolo);
    }

    @Test
    void falhaNaExclusaoVoltaAExecutarSemAvisarDeNovoEConfirmaUmaVez() {
        doThrow(new IllegalStateException("banco fora")).doNothing().when(entregas).removerDoDestinatario(titularSub);

        assertThrows(IllegalStateException.class, () -> useCase.executar(pedido(TipoTitular.IDOSO)));
        verify(publicador, never()).publicar(protocolo);

        useCase.executar(pedido(TipoTitular.IDOSO));

        verify(avisarRede, times(1)).avisar(protocolo);
        verify(publicador, times(1)).publicar(protocolo);
    }

    @Test
    void protocoloJaConcluidoPorOutraExecucaoNaoApagaNemConfirma() {
        protocolos.concluirDeFora(protocolo);

        useCase.executar(pedido(TipoTitular.IDOSO));

        verifyNoInteractions(avisarRede, dispositivos, entregas, notificacoes, alertas, publicador);
    }

    @Test
    void tokenDoTitularExcluidoEhRecusado() {
        DispositivoPushRepository repositorio = mock(DispositivoPushRepository.class);
        RegistrarTokenPushUseCase registrar = new RegistrarTokenPushUseCase(repositorio, titulares, transacao);

        registrar.registrar(titularSub, "pixel-7", "ExponentPushToken[a]", Plataforma.ANDROID);
        verify(repositorio, times(1)).salvar(org.mockito.ArgumentMatchers.any());

        useCase.executar(pedido(TipoTitular.FAMILIAR));

        assertThrows(TitularExcluidoException.class,
                () -> registrar.registrar(titularSub, "pixel-7", "ExponentPushToken[b]", Plataforma.ANDROID));
        verify(repositorio, times(1)).salvar(org.mockito.ArgumentMatchers.any());
        assertEquals(Set.of(titularSub), titulares.excluidos);
    }

    private static final class ProtocolosEmMemoria implements ProtocoloExclusaoRepository {

        private final Map<UUID, ProtocoloExclusao> estado = new HashMap<>();

        @Override
        public ProtocoloExclusao registrar(UUID protocolo) {
            return estado.computeIfAbsent(protocolo, id -> new ProtocoloExclusao(id, false, false));
        }

        @Override
        public void marcarAvisoEnviado(UUID protocolo) {
            ProtocoloExclusao atual = estado.get(protocolo);
            estado.put(protocolo, new ProtocoloExclusao(protocolo, true, atual.concluido()));
        }

        @Override
        public boolean concluir(UUID protocolo) {
            ProtocoloExclusao atual = estado.get(protocolo);
            if (atual.concluido()) {
                return false;
            }
            estado.put(protocolo, new ProtocoloExclusao(protocolo, atual.avisoEnviado(), true));
            return true;
        }

        void concluirDeFora(UUID protocolo) {
            estado.put(protocolo, new ProtocoloExclusao(protocolo, true, true));
        }
    }

    private static final class TitularesEmMemoria implements TitularExcluidoRepository {

        private final Set<UUID> excluidos = new HashSet<>();

        @Override
        public void travar(UUID usuarioId) {
        }

        @Override
        public void registrar(UUID usuarioId) {
            excluidos.add(usuarioId);
        }

        @Override
        public boolean existe(UUID usuarioId) {
            return excluidos.contains(usuarioId);
        }
    }
}
