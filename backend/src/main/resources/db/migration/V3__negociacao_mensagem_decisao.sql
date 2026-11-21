-- =====================================================================
-- Fase 6: negociação de troca.
--
-- O V1 já tinha as tabelas negociacao e proposta_negociacao, mas faltou onde guardar o
-- motivo de quem encerra a negociação (o colega que recusa ou o gestor que recusa).
-- Uma migração já aplicada NUNCA é editada: o Flyway compara o checksum e acusaria a
-- mudança. Por isso a correção vem numa migração nova.
-- =====================================================================

ALTER TABLE negociacao ADD COLUMN mensagem_decisao VARCHAR(500);

-- As buscas "este pedido já está numa negociação em aberto?" filtram por estas colunas.
CREATE INDEX ix_negociacao_solicitacao_colega      ON negociacao (solicitacao_colega_id);
CREATE INDEX ix_negociacao_solicitacao_solicitante ON negociacao (solicitacao_solicitante_id);
