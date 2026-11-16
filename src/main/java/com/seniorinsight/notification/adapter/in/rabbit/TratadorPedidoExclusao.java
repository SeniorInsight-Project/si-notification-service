package com.seniorinsight.notification.adapter.in.rabbit;

import java.time.Duration;

import com.fasterxml.jackson.databind.JsonNode;
import com.seniorinsight.notification.application.ExcluirTitularUseCase;
import com.seniorinsight.notification.application.Pausa;
import com.seniorinsight.notification.application.Retentativa;
import com.seniorinsight.notification.domain.model.PedidoExclusao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Valida o envelope e roda a exclusão. Falha temporária (banco, por exemplo) repete com intervalo até o limite; no
 * limite a exceção sobe e o listener manda a mensagem para a dead-letter. Envelope inválido sobe na hora.
 */
@Component
class TratadorPedidoExclusao {

    private static final Logger log = LoggerFactory.getLogger(TratadorPedidoExclusao.class);

    private final LeitorPedidoExclusao leitor;
    private final ExcluirTitularUseCase excluirTitular;
    private final Retentativa retentativa;

    TratadorPedidoExclusao(
            LeitorPedidoExclusao leitor,
            ExcluirTitularUseCase excluirTitular,
            Pausa pausa,
            @Value("${notification.exclusao.processamento.tentativas}") int tentativas,
            @Value("${notification.exclusao.processamento.intervalo}") Duration intervalo) {
        this.leitor = leitor;
        this.excluirTitular = excluirTitular;
        this.retentativa = new Retentativa(tentativas, intervalo, pausa);
    }

    void tratar(JsonNode envelope) {
        PedidoExclusao pedido = leitor.ler(envelope);
        log.info("Pedido de exclusão {} ({}) recebido.", pedido.protocolo(), pedido.tipo());
        retentativa.executar(() -> excluirTitular.executar(pedido));
    }
}
