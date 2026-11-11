-- Token de push passa a ser identificado pelo aparelho do usuário (PUT /v1/push-tokens).
ALTER TABLE notification.dispositivo_push ADD COLUMN identificador_aparelho VARCHAR(255);
UPDATE notification.dispositivo_push SET identificador_aparelho = id::text;
ALTER TABLE notification.dispositivo_push ALTER COLUMN identificador_aparelho SET NOT NULL;

ALTER TABLE notification.dispositivo_push DROP CONSTRAINT dispositivo_push_token_key;
ALTER TABLE notification.dispositivo_push ALTER COLUMN token TYPE VARCHAR(4096);
CREATE INDEX idx_dispositivo_push_token ON notification.dispositivo_push USING hash (token);
ALTER TABLE notification.dispositivo_push
    ADD CONSTRAINT uq_dispositivo_push_usuario_aparelho UNIQUE (usuario_id, identificador_aparelho);
ALTER TABLE notification.dispositivo_push
    ADD CONSTRAINT ck_dispositivo_push_plataforma CHECK (plataforma IN ('ANDROID', 'IOS'));

-- Reenvio do SOS sem leitura (RN-002): marca a entrega já reenviada.
ALTER TABLE notification.entrega ADD COLUMN reenviado_em TIMESTAMPTZ;
CREATE INDEX idx_entrega_sem_leitura
    ON notification.entrega (enviado_em)
    WHERE canal = 'PUSH' AND status = 'ENVIADA' AND lida_em IS NULL AND reenviado_em IS NULL;
