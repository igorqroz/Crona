package br.com.crona.ferias;

import java.util.EnumSet;
import java.util.Set;

/** Os status de uma negociação de troca (ver regras-de-negocio.md, seção 4). */
public enum StatusNegociacao {
    AGUARDANDO_COLEGA,
    AGUARDANDO_SOLICITANTE,
    AGUARDANDO_GESTOR,
    APROVADA,
    RECUSADA,
    CANCELADA;

    /** Ainda não terminou: um pedido só pode estar em UMA negociação em aberto por vez. */
    public static final Set<StatusNegociacao> EM_ABERTO =
            EnumSet.of(AGUARDANDO_COLEGA, AGUARDANDO_SOLICITANTE, AGUARDANDO_GESTOR);

    public boolean emAberto() {
        return EM_ABERTO.contains(this);
    }
}
