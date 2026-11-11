# si-notification-service

Serviço de notificações: consome os eventos do RabbitMQ (exchange `si.events`, fila `notification.q`) e entrega os alertas aos familiares por push (Expo Push) e e-mail, com confirmação de leitura. No MVP, o caso principal é o SOS (HU-065).

Parte do SeniorInsight — ver o Documento de Arquitetura de Software (seção 6) para as convenções.

Branches: `main` (principal, protegida) e `develop` (testes). Trabalhe em branches curtas por issue e abra PR para `develop`.

## Push do SOS (HU-065)

Fluxo: o `sos.updated` com status `DISPARADO` (CloudEvents, routing key `sos.updated`) vira uma notificação; a rede do idoso vem do care-service (`GET /internal/idosos/{id}/rede`, cache de 5 min) e cada familiar com conta recebe um push pelo Expo (prioridade alta, canal `sos`). Sem leitura em 60 s (RN-002), o push é reenviado e sai um e-mail ao familiar que não leu.

Rotas (contrato `si-contracts/openapi/notification-v1.yaml`, JWT do Keycloak): `PUT /v1/push-tokens`, `DELETE /v1/push-tokens/{identificadorAparelho}` e `PUT /v1/notificacoes/{notificacaoId}/leitura`.

| Variável | Uso |
| --- | --- |
| `KEYCLOAK_ISSUER_URI` | Issuer do realm que assina os JWT (obrigatória) |
| `CARE_BASE_URL` | URL do care-service (padrão `http://care-service:8080`) |
| `EXPO_ACCESS_TOKEN` | Token de acesso do Expo Push (opcional) |
| `NOTIFICATION_SOS_PRAZO_LEITURA` | Prazo sem leitura antes do reenvio (padrão `60s`) |
| `SPRING_MAIL_HOST`, `SPRING_MAIL_PORT`, `SPRING_MAIL_USERNAME`, `SPRING_MAIL_PASSWORD` | SMTP do e-mail de escalonamento; sem host, o e-mail não é enviado |
