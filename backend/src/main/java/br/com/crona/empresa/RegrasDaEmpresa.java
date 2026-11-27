package br.com.crona.empresa;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

/**
 * Cadastros 3: as regras de férias da empresa, como a API devolve (GET) e recebe (PUT).
 * Nulo = regra desligada (só nas que podem ser desligadas).
 *
 * @param percentualMinimoTrabalhando    EMP-01: quanto de cada departamento continua trabalhando (0 a 100)
 * @param antecedenciaMinimaDias         EMP-03: com quantos dias de antecedência pedir (30 ou mais, CLT-07)
 * @param limiteFeriasSimultaneasEmpresa EMP-04: quantas pessoas da empresa podem estar fora ao mesmo tempo
 * @param exigeConfirmacaoRh             EMP-06: o RH confirma depois do gestor
 */
public record RegrasDaEmpresa(
        @Min(value = 0, message = "O percentual vai de 0 a 100.")
        @Max(value = 100, message = "O percentual vai de 0 a 100.")
        Integer percentualMinimoTrabalhando,

        @Min(value = 30, message = "A antecedência mínima não pode ser menor que 30 dias.")
        @Max(value = 365, message = "A antecedência pode ser de no máximo 365 dias.")
        int antecedenciaMinimaDias,

        @Min(value = 1, message = "O limite precisa ser de pelo menos 1 pessoa.")
        Integer limiteFeriasSimultaneasEmpresa,

        boolean exigeConfirmacaoRh
) {
    static RegrasDaEmpresa de(ConfiguracaoFerias c) {
        return new RegrasDaEmpresa(c.getPercentualMinimoTrabalhando(), c.getAntecedenciaMinimaDias(),
                c.getLimiteFeriasSimultaneasEmpresa(), c.isExigeConfirmacaoRh());
    }
}
