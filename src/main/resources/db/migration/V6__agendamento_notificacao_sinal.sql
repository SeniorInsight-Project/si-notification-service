ALTER TABLE notification.notificacao
    ADD COLUMN programada_para TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    ADD COLUMN processada_em TIMESTAMPTZ;

UPDATE notification.notificacao
SET processada_em = CURRENT_TIMESTAMP
WHERE tipo <> 'TECNICO';

CREATE INDEX idx_notificacao_agendada
    ON notification.notificacao (programada_para)
    WHERE processada_em IS NULL;
