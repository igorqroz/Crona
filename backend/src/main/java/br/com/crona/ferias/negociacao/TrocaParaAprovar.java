package br.com.crona.ferias.negociacao;

import br.com.crona.ferias.aprovacao.ItemDeAprovacao.Situacao;
import br.com.crona.ferias.validacao.Verificacao;

import java.util.List;

/**
 * Uma troca na fila do gestor: a negociação + o checklist das datas novas de cada um,
 * avaliadas no cenário depois da troca.
 *
 * @param situacao a pior das duas: basta um lado com alerta para a troca ter alerta
 */
public record TrocaParaAprovar(
        NegociacaoResposta negociacao,
        Situacao situacao,
        List<Verificacao> verificacoesSolicitante,
        List<Verificacao> verificacoesColega
) {
}
