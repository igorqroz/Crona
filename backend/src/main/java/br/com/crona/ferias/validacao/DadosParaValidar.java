package br.com.crona.ferias.validacao;

import br.com.crona.ferias.SaldoDoPeriodo;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

/**
 * Tudo o que o validador precisa saber, já buscado do banco pelo service.
 * O validador em si não acessa banco nenhum: recebe os dados prontos e só aplica as regras.
 *
 * @param pedido               o que o colaborador escolheu
 * @param hoje                 a data de hoje (entra como dado, para os testes poderem escolher)
 * @param periodo              o período aquisitivo de onde saem os dias, com o saldo atual
 * @param partesJaPedidas      dias de descanso dos outros pedidos válidos do mesmo período (CLT-04)
 * @param abonoJaPedido        dias de abono já pedidos no mesmo período (CLT-06)
 * @param feriados             feriados que valem para o colaborador (CLT-05)
 * @param antecedenciaMinima   dias de antecedência configurados pela empresa (EMP-03, mínimo 30)
 * @param outrasFerias         as outras férias da pessoa (em análise ou aprovadas) que encostam nas datas (CRONA-01)
 */
public record DadosParaValidar(
        Pedido pedido,
        LocalDate hoje,
        Periodo periodo,
        List<Integer> partesJaPedidas,
        int abonoJaPedido,
        Set<LocalDate> feriados,
        int antecedenciaMinima,
        List<FeriasMarcadas> outrasFerias
) {

    public record Pedido(LocalDate inicio, int diasGozo, int diasAbono) {
    }

    public record Periodo(int numero, LocalDate fim, LocalDate limiteConcessao, int direito, int livres,
                          SaldoDoPeriodo.Situacao situacao) {
    }

    /** Férias que a pessoa já tem: um pedido em análise (aguardando gestor ou RH) ou aprovado. */
    public record FeriasMarcadas(LocalDate inicio, LocalDate fim, boolean aprovada) {
    }
}
