package br.com.crona.ferias;

import br.com.crona.ferias.validacao.Verificacao;

import java.time.LocalDate;
import java.util.List;

/**
 * Tudo o que a tela mostra ao lado do calendário: o resumo, o checklist e quem estará fora.
 *
 * @param resumo frase pronta para a tela, como no redesign ("1 ponto de atenção: o gestor verá isso.")
 */
public record SimulacaoResposta(
        LocalDate inicio,
        LocalDate fim,
        LocalDate voltaAoTrabalho,
        int periodo,
        int livresNoPeriodo,
        boolean podeEnviar,
        String resumo,
        List<Verificacao> verificacoes,
        List<ColegaFora> colegasNoPeriodo
) {

    public record ColegaFora(String nome, LocalDate inicio, LocalDate fim, boolean pendente) {
    }
}
