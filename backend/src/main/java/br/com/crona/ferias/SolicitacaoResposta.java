package br.com.crona.ferias;

import br.com.crona.compartilhado.ReferenciaResposta;

import java.time.Instant;
import java.time.LocalDate;

/** Um pedido de férias, como o colaborador vê em "Minhas solicitações". */
public record SolicitacaoResposta(
        Long id,
        StatusSolicitacao status,
        LocalDate inicio,
        LocalDate fim,
        int diasGozo,
        int diasAbono,
        int periodo,
        String recado,
        Instant enviadoEm,
        ReferenciaResposta decididoPor,
        String mensagemDecisao
) {

    static SolicitacaoResposta de(SolicitacaoFerias s) {
        var decidiu = s.getRhConfirmouPor() != null ? s.getRhConfirmouPor() : s.getGestorDecidiuPor();
        return new SolicitacaoResposta(s.getId(), s.getStatus(), s.getDataInicio(), s.getDataFim(),
                s.getDiasGozo(), s.getDiasAbono(), s.getPeriodoAquisitivo().getNumero(), s.getRecadoColaborador(),
                s.getCriadoEm(),
                decidiu == null ? null : new ReferenciaResposta(decidiu.getId(), decidiu.getNome()),
                s.getMensagemDecisao());
    }
}
