package br.com.crona.ferias.validacao;

import java.time.LocalDate;
import java.util.List;

/**
 * O que o validador da equipe precisa saber, já buscado do banco pelo service.
 *
 * @param departamento            o departamento do colaborador (nulo se ele não tem departamento)
 * @param ausenciasDoDepartamento pedidos dos COLEGAS do departamento que encostam no período
 * @param limiteDaEmpresa         EMP-04: máximo de pessoas fora ao mesmo tempo na empresa (nulo = regra desligada)
 * @param ausenciasDaEmpresa      pedidos de TODOS os outros colaboradores da empresa que encostam no período
 * @param restricoes              EMP-05: períodos restritos da empresa ou do departamento
 */
public record DadosDaEquipe(
        LocalDate inicio,
        LocalDate fim,
        Departamento departamento,
        List<Ausencia> ausenciasDoDepartamento,
        Integer limiteDaEmpresa,
        List<Ausencia> ausenciasDaEmpresa,
        List<Restricao> restricoes
) {

    /** @param percentualMinimoTrabalhando EMP-01 (nulo = regra desligada) */
    public record Departamento(String nome, int pessoas, Integer percentualMinimoTrabalhando) {
    }

    /** Férias de um colega. Pedidos ainda pendentes também contam (podem ser aprovados). */
    public record Ausencia(String nome, LocalDate inicio, LocalDate fim, boolean pendente) {
    }

    public record Restricao(String motivo, boolean bloqueia, LocalDate inicio, LocalDate fim) {
    }
}
