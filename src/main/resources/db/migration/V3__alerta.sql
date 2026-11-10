-- Central de alertas (HU-034/035/037): cada alerta enviado à rede do idoso, com a avaliação útil/falso.
CREATE TABLE notification.alerta (
    id UUID PRIMARY KEY,
    origem_id UUID NOT NULL UNIQUE,
    idoso_id UUID NOT NULL,
    tipo VARCHAR(20) NOT NULL,
    titulo VARCHAR(160) NOT NULL,
    detalhe VARCHAR(1000),
    gerado_em TIMESTAMPTZ NOT NULL,
    avaliacao VARCHAR(10),
    avaliado_em TIMESTAMPTZ,
    motivo_avaliacao VARCHAR(500),
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_alerta_tipo CHECK (tipo IN ('SOS', 'CRITICO', 'ATENCAO', 'TECNICO')),
    CONSTRAINT ck_alerta_avaliacao CHECK (avaliacao IN ('UTIL', 'FALSO')),
    CONSTRAINT ck_alerta_avaliacao_completa CHECK ((avaliacao IS NULL) = (avaliado_em IS NULL))
);

CREATE INDEX idx_alerta_idoso_gerado ON notification.alerta (idoso_id, gerado_em DESC);

-- SOS já recebidos antes desta migration entram na central.
INSERT INTO notification.alerta (id, origem_id, idoso_id, tipo, titulo, detalhe, gerado_em)
SELECT id, evento_id, idoso_id, 'SOS', titulo, mensagem, ocorrido_em
FROM notification.notificacao
WHERE tipo = 'SOS';
