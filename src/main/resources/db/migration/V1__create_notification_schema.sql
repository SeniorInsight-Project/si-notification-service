CREATE SCHEMA IF NOT EXISTS notification;

CREATE TABLE notification.dispositivo_push (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    usuario_id UUID NOT NULL,
    token VARCHAR(512) NOT NULL UNIQUE,
    plataforma VARCHAR(30) NOT NULL,
    ativo BOOLEAN NOT NULL DEFAULT TRUE,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    atualizado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_dispositivo_push_usuario_ativo
    ON notification.dispositivo_push (usuario_id, ativo);

CREATE TABLE notification.notificacao (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    evento_id UUID NOT NULL UNIQUE,
    tipo VARCHAR(40) NOT NULL,
    idoso_id UUID NOT NULL,
    titulo VARCHAR(160) NOT NULL,
    mensagem VARCHAR(1000) NOT NULL,
    ocorrido_em TIMESTAMPTZ NOT NULL,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notificacao_idoso_ocorrido
    ON notification.notificacao (idoso_id, ocorrido_em DESC);

CREATE TABLE notification.entrega (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    notificacao_id UUID NOT NULL REFERENCES notification.notificacao (id) ON DELETE CASCADE,
    destinatario_id UUID NOT NULL,
    canal VARCHAR(30) NOT NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'PENDENTE',
    enviado_em TIMESTAMPTZ,
    lida_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_entrega_notificacao_destinatario_canal
        UNIQUE (notificacao_id, destinatario_id, canal),
    CONSTRAINT ck_entrega_status
        CHECK (status IN ('PENDENTE', 'ENVIADA', 'FALHA', 'LIDA')),
    CONSTRAINT ck_entrega_confirmacao_leitura
        CHECK ((status = 'LIDA') = (lida_em IS NOT NULL))
);

CREATE INDEX idx_entrega_destinatario_status
    ON notification.entrega (destinatario_id, status);
