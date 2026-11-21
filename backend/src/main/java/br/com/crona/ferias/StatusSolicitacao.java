package br.com.crona.ferias;

/** Os status de uma solicitação de férias (ver regras-de-negocio.md, seção 4). */
public enum StatusSolicitacao {
    AGUARDANDO_GESTOR,
    AGUARDANDO_RH,
    APROVADA,
    RECUSADA,
    CANCELADA;

    /**
     * Pedidos em andamento já "reservam" os dias: sem isso, dava para fazer dois pedidos
     * de 30 dias no mesmo período enquanto o primeiro espera o gestor.
     */
    public boolean consomeSaldo() {
        return this == AGUARDANDO_GESTOR || this == AGUARDANDO_RH || this == APROVADA;
    }

    /** Os status que "ocupam" dias: valem para saldo, divisão e pessoas fora. */
    public static final java.util.Set<StatusSolicitacao> EM_ANDAMENTO_OU_APROVADA =
            java.util.EnumSet.of(AGUARDANDO_GESTOR, AGUARDANDO_RH, APROVADA);
}
