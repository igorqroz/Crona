-- =====================================================================
-- Cancelamento de férias já aprovadas (pelo RH).
--
-- Um erro humano numa aprovação precisa ter volta, mas sem apagar o que aconteceu: o pedido
-- passa para CANCELADA e estas colunas guardam QUEM cancelou, QUANDO e POR QUÊ. A mensagem
-- da aprovação (mensagem_decisao) continua lá, intacta.
--
-- Quando é o próprio colaborador que desiste de um pedido em análise, elas ficam vazias.
-- =====================================================================

ALTER TABLE solicitacao_ferias
    ADD COLUMN cancelado_por_id    BIGINT REFERENCES colaborador (id),
    ADD COLUMN cancelado_em        TIMESTAMPTZ,
    ADD COLUMN motivo_cancelamento VARCHAR(500);
