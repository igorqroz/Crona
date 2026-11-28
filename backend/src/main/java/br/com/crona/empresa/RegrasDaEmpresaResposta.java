package br.com.crona.empresa;

/**
 * Cadastros 3: o que a tela de Regras recebe. As regras (iguais às do PUT) e dois números de contexto,
 * que só servem para a tela explicar o efeito de cada regra:
 *
 * @param pessoasAtivas       quantas pessoas com acesso ativo a empresa tem (dica do limite da empresa, EMP-04)
 * @param pedidosAguardandoRh quantos pedidos estão esperando o RH (aviso ao desligar a EMP-06)
 */
public record RegrasDaEmpresaResposta(
        Integer percentualMinimoTrabalhando,
        int antecedenciaMinimaDias,
        Integer limiteFeriasSimultaneasEmpresa,
        boolean exigeConfirmacaoRh,
        long pessoasAtivas,
        long pedidosAguardandoRh
) {
    static RegrasDaEmpresaResposta de(RegrasDaEmpresa regras, long pessoasAtivas, long pedidosAguardandoRh) {
        return new RegrasDaEmpresaResposta(regras.percentualMinimoTrabalhando(), regras.antecedenciaMinimaDias(),
                regras.limiteFeriasSimultaneasEmpresa(), regras.exigeConfirmacaoRh(), pessoasAtivas, pedidosAguardandoRh);
    }
}
