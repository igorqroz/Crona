package br.com.crona.ferias.aprovacao;

import br.com.crona.compartilhado.ReferenciaResposta;
import br.com.crona.ferias.SimulacaoResposta;
import br.com.crona.ferias.validacao.Verificacao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Um pedido na fila de aprovação do gestor (tela "Aprovações" do redesign).
 *
 * @param situacao resume as verificações: SEM_CONFLITO, COM_ATENCAO ou COM_IMPEDIMENTO
 * @param alertas  só as verificações que não passaram, para a lista mostrar o motivo
 */
public record ItemDeAprovacao(
        Long id,
        ReferenciaResposta colaborador,
        String departamento,
        LocalDate inicio,
        LocalDate fim,
        int diasGozo,
        int diasAbono,
        int periodo,
        String recado,
        Instant enviadoEm,
        Situacao situacao,
        List<Verificacao> alertas
) {

    public enum Situacao { SEM_CONFLITO, COM_ATENCAO, COM_IMPEDIMENTO }

    /** O detalhe de um pedido: o item + o checklist completo e quem estará fora. */
    public record Detalhe(ItemDeAprovacao pedido, int livresNoPeriodo, List<Verificacao> verificacoes,
                          List<SimulacaoResposta.ColegaFora> colegasNoPeriodo) {
    }
}
