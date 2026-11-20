package br.com.crona.departamento;

/**
 * O que a API DEVOLVE sobre um departamento.
 *
 * Só os campos que a tela precisa. A entidade nunca sai do back-end (armadilha A-01):
 * se um dia ela ganhar um campo interno, ele não vaza na API por acidente.
 */
public record DepartamentoResposta(Long id, String nome) {

    static DepartamentoResposta de(Departamento departamento) {
        return new DepartamentoResposta(departamento.getId(), departamento.getNome());
    }
}
