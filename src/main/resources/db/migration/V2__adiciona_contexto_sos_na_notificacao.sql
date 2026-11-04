-- Contexto do acionamento do SOS, que antes chegava no evento e era descartado.
-- Todas as colunas são opcionais: um SOS sem localização ou bateria continua sendo um SOS.
ALTER TABLE notification.notificacao
    ADD COLUMN latitude DOUBLE PRECISION,
    ADD COLUMN longitude DOUBLE PRECISION,
    ADD COLUMN nivel_bateria SMALLINT,
    ADD COLUMN observacao VARCHAR(500),
    ADD CONSTRAINT ck_notificacao_localizacao_completa
        CHECK ((latitude IS NULL) = (longitude IS NULL)),
    ADD CONSTRAINT ck_notificacao_latitude
        CHECK (latitude BETWEEN -90 AND 90),
    ADD CONSTRAINT ck_notificacao_longitude
        CHECK (longitude BETWEEN -180 AND 180),
    ADD CONSTRAINT ck_notificacao_nivel_bateria
        CHECK (nivel_bateria BETWEEN 0 AND 100);
