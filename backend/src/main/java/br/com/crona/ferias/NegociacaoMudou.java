package br.com.crona.ferias;

/**
 * Evento de domínio: algo aconteceu numa negociação de troca. Ver {@link SolicitacaoMudou}.
 *
 * @param autorId quem fez a ação (para não notificar a própria pessoa)
 */
public record NegociacaoMudou(Long negociacaoId, Acao acao, Long autorId) {

    public enum Acao { PROPOSTA, CONTRAPROPOSTA, ACEITA, RECUSADA, CANCELADA, APROVADA, RECUSADA_PELO_GESTOR }
}
