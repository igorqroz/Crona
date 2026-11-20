package br.com.crona.seguranca;

import br.com.crona.colaborador.Perfil;

import java.util.EnumSet;
import java.util.Set;

/**
 * O que cada pessoa PODE FAZER no sistema (padrão PAD-02 em docs/padroes-de-referencia.md).
 *
 * Os controllers conferem PERMISSÕES, nunca perfis:
 *     @PreAuthorize("hasAuthority('COLABORADOR_GERENCIAR')")    e não    hasRole('ADMIN')
 *
 * Por quê? A relação perfil → permissões fica TODA aqui, num lugar só. Se amanhã o gestor
 * puder cadastrar colaboradores, muda uma linha neste arquivo e nenhum controller.
 * E se um dia os perfis ficarem configuráveis pelo cliente, os controllers continuam iguais.
 */
public enum Permissao {

    DEPARTAMENTO_VER,
    DEPARTAMENTO_GERENCIAR,     // criar, renomear, excluir
    COLABORADOR_VER,
    COLABORADOR_GERENCIAR;      // cadastrar, alterar, desativar, reativar

    /** O mapa perfil → permissões. */
    public static Set<Permissao> doPerfil(Perfil perfil) {
        return switch (perfil) {
            // Colaborador: vê os departamentos (para escolher datas, ver a equipe...).
            case COLABORADOR -> EnumSet.of(DEPARTAMENTO_VER);
            // Gestor: também consulta colaboradores (a equipe dele, para aprovar férias).
            case GESTOR -> EnumSet.of(DEPARTAMENTO_VER, COLABORADOR_VER);
            // Administrador (RH): tudo.
            case ADMIN -> EnumSet.allOf(Permissao.class);
        };
    }
}
