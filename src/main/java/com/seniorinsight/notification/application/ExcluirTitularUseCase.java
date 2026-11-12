package com.seniorinsight.notification.application;

import com.seniorinsight.notification.domain.model.PedidoExclusao;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HU-022: trata um pedido de exclusão dos dados do titular. Para IDOSO, avisa a rede antes, para o aviso não usar
 * dados já apagados. Depois, numa só transação, apaga os dados, marca o titular como excluído e grava a confirmação
 * na outbox. Reentrega do mesmo protocolo não repete o aviso nem a confirmação.
 */
public class ExcluirTitularUseCase {

    private static final Logger log = LoggerFactory.getLogger(ExcluirTitularUseCase.class);

    private final ProtocoloExclusaoRepository protocolos;
    private final TitularExcluidoRepository titulares;
    private final AvisarRedeExclusaoUseCase avisarRede;
    private final Transacao transacao;
    private final DispositivoPushRepository dispositivos;
    private final EntregaRepository entregas;
    private final NotificacaoRepository notificacoes;
    private final AlertaRepository alertas;
    private final PublicadorExclusaoConcluida publicador;

    public ExcluirTitularUseCase(
            ProtocoloExclusaoRepository protocolos,
            TitularExcluidoRepository titulares,
            AvisarRedeExclusaoUseCase avisarRede,
            Transacao transacao,
            DispositivoPushRepository dispositivos,
            EntregaRepository entregas,
            NotificacaoRepository notificacoes,
            AlertaRepository alertas,
            PublicadorExclusaoConcluida publicador) {
        this.protocolos = protocolos;
        this.titulares = titulares;
        this.avisarRede = avisarRede;
        this.transacao = transacao;
        this.dispositivos = dispositivos;
        this.entregas = entregas;
        this.notificacoes = notificacoes;
        this.alertas = alertas;
        this.publicador = publicador;
    }

    public void executar(PedidoExclusao pedido) {
        ProtocoloExclusao protocolo = protocolos.registrar(pedido.protocolo());
        if (protocolo.concluido()) {
            log.info("Protocolo {} de exclusão já concluído; ignorado.", pedido.protocolo());
            return;
        }
        if (pedido.tipo() == TipoTitular.IDOSO && !protocolo.avisoEnviado()) {
            avisarRede.avisar(pedido.protocolo());
            protocolos.marcarAvisoEnviado(pedido.protocolo());
        }
        transacao.executar(() -> excluirEConfirmar(pedido));
    }

    private void excluirEConfirmar(PedidoExclusao pedido) {
        if (!protocolos.concluir(pedido.protocolo())) {
            return;
        }
        titulares.travar(pedido.titularSub());
        titulares.registrar(pedido.titularSub());
        dispositivos.removerTodos(pedido.titularSub());
        entregas.removerDoDestinatario(pedido.titularSub());
        if (pedido.tipo() == TipoTitular.IDOSO) {
            notificacoes.removerDoIdoso(pedido.titularId());
            alertas.removerDoIdoso(pedido.titularId());
        }
        publicador.publicar(pedido.protocolo());
        log.info("Exclusão {} ({}) concluída no notification-service.", pedido.protocolo(), pedido.tipo());
    }
}
