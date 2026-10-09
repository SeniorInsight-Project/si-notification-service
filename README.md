# si-notification-service

Serviço de notificações: consome os eventos do RabbitMQ (exchange `si.events`, fila `notification.q`) e entrega os alertas aos familiares por push (Expo Push) e e-mail, com confirmação de leitura. No MVP, o caso principal é o SOS (HU-065).

Parte do SeniorInsight — ver o Documento de Arquitetura de Software (seção 6) para as convenções.

Branches: `main` (principal, protegida) e `develop` (testes). Trabalhe em branches curtas por issue e abra PR para `develop`.
