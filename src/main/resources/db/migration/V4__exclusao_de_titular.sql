-- Exclusão dos dados do titular (HU-022, lgpd.erasure.requested).

-- Andamento de cada protocolo: reentrega do mesmo protocolo não repete o aviso nem a confirmação.
CREATE TABLE notification.exclusao_protocolo (
    protocolo UUID PRIMARY KEY,
    aviso_enviado_em TIMESTAMPTZ,
    concluido_em TIMESTAMPTZ,
    criado_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Só o SHA-256 do sub do Keycloak, para recusar a recriação de dados de quem já foi excluído.
CREATE TABLE notification.titular_excluido (
    usuario_hash CHAR(64) PRIMARY KEY,
    excluido_em TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
