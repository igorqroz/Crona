package br.com.crona.colaborador;

/**
 * Perfis de acesso. Guardados no banco como TEXTO ('GESTOR'), nunca como número:
 * reordenar o enum não estraga os dados (ver modelo-de-dominio.md, 3.8).
 */
public enum Perfil {
    COLABORADOR,
    GESTOR,
    ADMIN;

    /** Só gestor ou administrador pode ser gestor de alguém. */
    public boolean podeGerenciarEquipe() {
        return this == GESTOR || this == ADMIN;
    }
}
