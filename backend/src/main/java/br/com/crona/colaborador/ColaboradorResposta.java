package br.com.crona.colaborador;

import br.com.crona.compartilhado.ReferenciaResposta;

import java.time.LocalDate;

/**
 * O que a API devolve sobre um colaborador. Sem senha, sem hash, sem empresa (armadilha A-01).
 */
public record ColaboradorResposta(
        Long id,
        String nome,
        String email,
        Perfil perfil,
        String cargo,
        LocalDate dataAdmissao,
        boolean ativo,
        ReferenciaResposta departamento,
        ReferenciaResposta gestor
) {

    static ColaboradorResposta de(Colaborador c) {
        var departamento = c.getDepartamento() == null ? null
                : new ReferenciaResposta(c.getDepartamento().getId(), c.getDepartamento().getNome());
        var gestor = c.getGestor() == null ? null
                : new ReferenciaResposta(c.getGestor().getId(), c.getGestor().getNome());
        return new ColaboradorResposta(c.getId(), c.getNome(), c.getEmail(), c.getPerfil(), c.getCargo(),
                c.getDataAdmissao(), c.isAtivo(), departamento, gestor);
    }
}
