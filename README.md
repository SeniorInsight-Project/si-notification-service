# si-notification-service

Serviço de notificações: consome os eventos do RabbitMQ (exchange `si.events`, fila `notification.q`) e entrega os alertas aos familiares por push (Expo Push) e e-mail, com confirmação de leitura. No MVP, o caso principal é o SOS (HU-065).

Parte do SeniorInsight — ver o Documento de Arquitetura de Software (seção 6) para as convenções.

Branches: `main` (principal, protegida) e `develop` (testes). Trabalhe em branches curtas por issue e abra PR para `develop`.

## Push do SOS (HU-065)

Fluxo: o `sos.updated` com status `DISPARADO` (CloudEvents, routing key `sos.updated`) vira uma notificação; a rede do idoso vem do care-service (`GET /internal/idosos/{id}/rede`, cache de 5 min) e cada familiar com conta recebe um push pelo Expo (prioridade alta, canal `sos`). Sem leitura em 60 s (RN-002), o push é reenviado e sai um e-mail ao familiar que não leu.

Rotas (contrato `si-contracts/openapi/notification-v1.yaml`, JWT do Keycloak): `PUT /v1/push-tokens`, `DELETE /v1/push-tokens/{identificadorAparelho}` e `PUT /v1/notificacoes/{notificacaoId}/leitura`.

## Exclusão dos dados do titular (HU-022)

O `lgpd.erasure.requested` (CloudEvents, enviado pelo report-service) chega na mesma fila. O envelope é validado (`source: /report-service`, `type`, `dataschema`, `id`, `subject` igual ao `protocolo`); se estiver fora do contrato, a mensagem vai para a `notification.dlq`.

1. **IDOSO:** busca a rede em `GET /internal/exclusoes/{protocolo}/rede` do care-service (404 = o care ainda não processou: tenta de novo com intervalo) e avisa cada membro por push e e-mail, sem dado de saúde. Se o limite estourar, registra erro e a exclusão segue.
2. **Exclusão:** apaga os tokens de push e as entregas do `titularSub` (o `sub` do Keycloak, que é o `usuario_id` dos tokens e o `destinatario_id` das entregas); para IDOSO, também as notificações e os alertas do `titularId` (`idoso_id`), com as entregas em cascata. O `sub` fica guardado só como hash SHA-256, para o `PUT /v1/push-tokens` recusar (403) quem já foi excluído.
3. **Confirmação:** grava `lgpd.erasure.completed` (`servico: notification-service`) na `outbox_evento`, na mesma transação da exclusão; o relay publica na exchange `si.events`.

O protocolo fica em `exclusao_protocolo`: reentrega do mesmo protocolo não repete o aviso nem a confirmação. Falha temporária (banco) repete com intervalo; esgotado o limite, a mensagem vai para a `notification.dlq` com log de erro.

| Variável | Uso |
| --- | --- |
| `KEYCLOAK_ISSUER_URI` | Issuer do realm que assina os JWT (obrigatória) |
| `CARE_BASE_URL` | URL do care-service (padrão `http://care-service:8080`) |
| `NOTIFICATION_RABBITMQ_ROUTING_KEYS` | Routing keys ligadas à fila, separadas por vírgula (padrão `sos.updated,lgpd.erasure.requested`) |
| `NOTIFICATION_EXCLUSAO_AVISO_TENTATIVAS`, `NOTIFICATION_EXCLUSAO_AVISO_INTERVALO` | Limite e intervalo da busca da rede no care (padrão `10` e `2s`) |
| `NOTIFICATION_EXCLUSAO_TENTATIVAS`, `NOTIFICATION_EXCLUSAO_INTERVALO` | Limite e intervalo da nova tentativa da exclusão (padrão `3` e `5s`) |
| `NOTIFICATION_OUTBOX_RELAY_INTERVALO`, `NOTIFICATION_OUTBOX_RELAY_TAMANHO_LOTE`, `NOTIFICATION_OUTBOX_RELAY_TIMEOUT_CONFIRMACAO` | Relay da outbox (padrão `1s`, `100` e `5s`) |
| `EXPO_ACCESS_TOKEN` | Token de acesso do Expo Push (opcional) |
| `NOTIFICATION_SOS_PRAZO_LEITURA` | Prazo sem leitura antes do reenvio (padrão `60s`) |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP do e-mail de escalonamento; sem host, o e-mail não é enviado |
