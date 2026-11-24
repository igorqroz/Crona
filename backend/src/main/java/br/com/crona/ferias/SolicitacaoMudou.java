package br.com.crona.ferias;

/**
 * Evento de domínio: algo aconteceu com um pedido de férias.
 *
 * Quem publica (os services) não sabe quem vai reagir. Hoje quem escuta é o Notificador
 * (Fase 8b); amanhã pode ser a auditoria (PAD-01) ou um e-mail, sem mexer nos services.
 *
 * @param autorId quem fez a ação (para não notificar a própria pessoa)
 */
public record SolicitacaoMudou(Long solicitacaoId, Acao acao, Long autorId) {

    public enum Acao { ENVIADA, CANCELADA, APROVADA_PELO_GESTOR, CONFIRMADA_PELO_RH, RECUSADA, APROVADA_CANCELADA_PELO_RH }
}
