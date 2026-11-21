package br.com.crona.ferias;

import br.com.crona.compartilhado.ReferenciaResposta;

import java.time.LocalDate;
import java.util.List;

/**
 * O que o calendário da tela "Solicitar férias" precisa para os meses que estão na tela.
 *
 * @param departamento o meu departamento (nulo se não tenho)
 * @param limite       EMP-01: quantas pessoas do departamento podem estar fora ao mesmo tempo
 *                     (nulo se não tenho departamento ou a regra está desligada)
 * @param gestor       quem vai receber o pedido (o botão "Enviar para Roberto Neves")
 * @param ausencias    as férias dos colegas do MEU departamento que encostam no intervalo
 *                     (aprovadas e em análise: as em análise também podem acabar aprovadas)
 * @param feriados     os feriados do intervalo, para o calendário marcar os dias ruins (CLT-05)
 */
public record CalendarioDaEquipe(
        String departamento,
        Integer limite,
        ReferenciaResposta gestor,
        List<Ausencia> ausencias,
        List<LocalDate> feriados
) {

    public record Ausencia(String nome, LocalDate inicio, LocalDate fim, boolean pendente) {
    }
}
