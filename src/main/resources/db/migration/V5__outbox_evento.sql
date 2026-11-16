-- Eventos gravados na transação do caso de uso e publicados depois na exchange si.events (padrão outbox).
CREATE TABLE notification.outbox_evento (
    id UUID PRIMARY KEY,
    tipo_agregado VARCHAR(100) NOT NULL,
    agregado_id UUID NOT NULL,
    tipo_evento VARCHAR(150) NOT NULL,
    payload JSONB NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    publicado_em TIMESTAMPTZ,
    tentativas INTEGER NOT NULL DEFAULT 0
);

COMMENT ON COLUMN notification.outbox_evento.tipo_evento IS 'Routing key na exchange si.events, ex.: lgpd.erasure.completed';

-- O relay só lê os pendentes, em ordem de criação.
CREATE INDEX idx_outbox_evento_pendentes ON notification.outbox_evento (criado_em) WHERE publicado_em IS NULL;
