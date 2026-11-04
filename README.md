# si-notification-service

Serviço de notificações: consome os eventos do RabbitMQ (exchange `si.events`, fila `notification.q`) e entrega os alertas aos familiares por push (Expo Push) e e-mail, com confirmação de leitura. No MVP, o caso principal é o SOS (HU-065).

Parte do SeniorInsight — ver o Documento de Arquitetura de Software (seção 6) para as convenções.

Branches: `main` (principal, protegida) e `develop` (testes). Trabalhe em branches curtas por issue e abra PR para `develop`.

## Rodando localmente

Suba o Postgres e o RabbitMQ pelo `compose.yaml` do `si-infra` e rode com o perfil `local`, que usa as mesmas credenciais padrão do compose:

```bash
SPRING_PROFILES_ACTIVE=local mvn spring-boot:run
```

Fora do perfil `local` não há credenciais padrão: `DB_USERNAME`, `DB_PASSWORD`, `RABBITMQ_USERNAME` e `RABBITMQ_PASSWORD` precisam vir do ambiente (em produção, dos secrets do Swarm).

## Testes

```bash
mvn verify
```

Roda os testes unitários, as regras de arquitetura (ArchUnit) e os testes de integração (`*IT`), que sobem um Postgres com Testcontainers e por isso precisam de Docker. O workflow `CI` executa o mesmo comando em todo PR para `develop`/`main`.

## Tratamento de falhas no consumo

Cada mensagem da fila `notification.q` termina em ack ou nack:

| Situação | Destino |
|---|---|
| Evento processado (novo ou duplicado) | ack |
| Falha transitória: banco inacessível, timeout, deadlock | volta para a fila depois de `notification.rabbitmq.retry-delay` (padrão 5s) |
| Falha permanente: JSON inválido, evento sem `alarmeId`/`idosoId`, violação de constraint, erro inesperado | `notification.dlq` |

O `DeadLetterQueueMonitor` consulta a DLQ a cada minuto, publica a métrica `notification.dlq.mensagens` e loga um WARN enquanto houver mensagens lá. Cada uma delas é um SOS que não virou notificação.
