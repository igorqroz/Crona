package br.com.crona.ferias.negociacao;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.compartilhado.ReferenciaResposta;
import br.com.crona.ferias.Negociacao;
import br.com.crona.ferias.PropostaNegociacao;
import br.com.crona.ferias.SolicitacaoFerias;
import br.com.crona.ferias.StatusNegociacao;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * Uma negociação de troca, como os dois colegas (e o gestor) veem.
 *
 * @param vezDe          quem precisa responder agora (nulo quando está com o gestor ou já terminou)
 * @param pedidoDoColega as férias do colega, nas datas de HOJE (mudam só quando o gestor aprova)
 * @param propostas      o histórico da conversa; a última é a vigente
 */
public record NegociacaoResposta(
        Long id,
        StatusNegociacao status,
        ReferenciaResposta solicitante,
        ReferenciaResposta colega,
        ReferenciaResposta vezDe,
        Ferias pedidoDoColega,
        Ferias pedidoDoSolicitante,
        List<Proposta> propostas,
        ReferenciaResposta decididoPor,
        String mensagemDecisao,
        Instant criadaEm
) {

    public record Ferias(Long id, LocalDate inicio, LocalDate fim, int diasGozo) {
    }

    public record Proposta(ReferenciaResposta autor, LocalDate inicioSolicitante, LocalDate fimSolicitante,
                           LocalDate inicioColega, LocalDate fimColega, String mensagem, Instant feitaEm) {
    }

    public static NegociacaoResposta de(Negociacao n) {
        return new NegociacaoResposta(n.getId(), n.getStatus(), ref(n.getSolicitante()), ref(n.getColega()),
                ref(n.vezDe()), ferias(n.getPedidoDoColega()), ferias(n.getPedidoDoSolicitante()),
                n.getPropostas().stream().map(NegociacaoResposta::proposta).toList(),
                ref(n.getDecididoPor()), n.getMensagemDecisao(), n.getCriadoEm());
    }

    private static Proposta proposta(PropostaNegociacao p) {
        return new Proposta(ref(p.getAutor()), p.getInicioSolicitante(), p.getFimSolicitante(),
                p.getInicioColega(), p.getFimColega(), p.getMensagem(), p.getCriadoEm());
    }

    private static Ferias ferias(SolicitacaoFerias s) {
        return s == null ? null : new Ferias(s.getId(), s.getDataInicio(), s.getDataFim(), s.getDiasGozo());
    }

    private static ReferenciaResposta ref(Colaborador c) {
        return c == null ? null : new ReferenciaResposta(c.getId(), c.getNome());
    }
}
