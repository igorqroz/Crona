package br.com.crona.departamento;

/**
 * Uma linha da lista de departamentos (GET /departamentos), com o resumo da tela de Cadastros:
 * "5 pessoas · até 1 fora por dia".
 *
 * @param pessoas    quantas pessoas ATIVAS estão no departamento
 * @param limiteFora EMP-01: quantas podem estar de férias no mesmo dia; nulo se a regra está desligada
 */
public record DepartamentoResumo(Long id, String nome, int pessoas, Integer limiteFora) {
}
