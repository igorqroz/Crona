package br.com.crona.notificacao;

/** Os tipos de notificação (PAD-03). O tipo permite filtrar e, no futuro, escolher quais receber. */
public enum TipoDeNotificacao {
    /** Para o gestor: um pedido da equipe chegou. */
    SOLICITACAO_PENDENTE,
    /** Para o gestor: alguém da equipe desistiu de um pedido. */
    SOLICITACAO_CANCELADA,
    /** Para o colaborador: o pedido foi aprovado, confirmado ou recusado. */
    SOLICITACAO_DECIDIDA,
    /** Para o RH: um pedido aprovado pelo gestor espera a confirmação (EMP-06). */
    CONFIRMACAO_RH_PENDENTE,
    /** Para o colega: chegou uma proposta ou contraproposta de troca. */
    TROCA_RECEBIDA,
    /** Para quem participa: o outro aceitou, recusou ou cancelou. */
    TROCA_ATUALIZADA,
    /** Para o gestor: uma troca combinada espera a aprovação. */
    TROCA_PENDENTE,
    /** Para os dois colegas: o gestor aprovou ou recusou a troca. */
    TROCA_DECIDIDA,
    /** Rotina diária: o prazo para tirar as férias está acabando (CLT-03). */
    FERIAS_A_VENCER
}
