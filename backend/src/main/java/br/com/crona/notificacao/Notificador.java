package br.com.crona.notificacao;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.colaborador.Perfil;
import br.com.crona.ferias.Negociacao;
import br.com.crona.ferias.NegociacaoMudou;
import br.com.crona.ferias.NegociacaoRepository;
import br.com.crona.ferias.SolicitacaoFerias;
import br.com.crona.ferias.SolicitacaoFeriasRepository;
import br.com.crona.ferias.SolicitacaoMudou;
import br.com.crona.ferias.StatusSolicitacao;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Objects;

import static br.com.crona.notificacao.TipoDeNotificacao.CONFIRMACAO_RH_PENDENTE;
import static br.com.crona.notificacao.TipoDeNotificacao.SOLICITACAO_CANCELADA;
import static br.com.crona.notificacao.TipoDeNotificacao.SOLICITACAO_DECIDIDA;
import static br.com.crona.notificacao.TipoDeNotificacao.SOLICITACAO_PENDENTE;
import static br.com.crona.notificacao.TipoDeNotificacao.TROCA_ATUALIZADA;
import static br.com.crona.notificacao.TipoDeNotificacao.TROCA_DECIDIDA;
import static br.com.crona.notificacao.TipoDeNotificacao.TROCA_PENDENTE;
import static br.com.crona.notificacao.TipoDeNotificacao.TROCA_RECEBIDA;

/**
 * Escuta os eventos do fluxo de férias e decide QUEM é avisado e COM QUAL TEXTO.
 *
 * @EventListener comum (síncrono): roda na MESMA transação de quem publicou o evento. Se a ação
 * falhar e for desfeita (rollback), a notificação também some: nunca existe aviso de algo que
 * não aconteceu.
 *
 * Regra geral: ninguém é notificado da própria ação.
 */
@Component
class Notificador {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private final Notificacoes notificacoes;
    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final NegociacaoRepository negociacaoRepository;
    private final ColaboradorRepository colaboradorRepository;

    Notificador(Notificacoes notificacoes, SolicitacaoFeriasRepository solicitacaoRepository,
                NegociacaoRepository negociacaoRepository, ColaboradorRepository colaboradorRepository) {
        this.notificacoes = notificacoes;
        this.solicitacaoRepository = solicitacaoRepository;
        this.negociacaoRepository = negociacaoRepository;
        this.colaboradorRepository = colaboradorRepository;
    }

    // ================================================================== pedidos

    @EventListener
    void aoMudarSolicitacao(SolicitacaoMudou evento) {
        SolicitacaoFerias s = solicitacaoRepository.findById(evento.solicitacaoId()).orElseThrow();
        Colaborador colaborador = s.getColaborador();
        Colaborador autor = colaboradorRepository.findById(evento.autorId()).orElseThrow();
        String ferias = intervalo(s.getDataInicio(), s.getDataFim());

        switch (evento.acao()) {
            case ENVIADA -> avisar(colaborador.getGestor(), autor, SOLICITACAO_PENDENTE,
                    colaborador.getNome() + " pediu férias de " + ferias + " (" + s.getDiasGozo() + " dias).",
                    "/aprovacoes");

            case CANCELADA -> avisar(colaborador.getGestor(), autor, SOLICITACAO_CANCELADA,
                    colaborador.getNome() + " cancelou o pedido de férias de " + ferias + ".", "/aprovacoes");

            case APROVADA_PELO_GESTOR -> {
                if (s.getStatus() == StatusSolicitacao.AGUARDANDO_RH) {
                    avisar(colaborador, autor, SOLICITACAO_DECIDIDA, autor.getNome() + " aprovou suas férias de "
                            + ferias + ". Agora falta a confirmação do RH.", "/inicio");
                    for (Colaborador rh : colaboradorRepository.findByEmpresaIdAndPerfilAndAtivoTrue(
                            colaborador.getEmpresa().getId(), Perfil.ADMIN)) {
                        avisar(rh, autor, CONFIRMACAO_RH_PENDENTE, "As férias de " + colaborador.getNome()
                                + " (" + ferias + ") esperam a confirmação do RH.", "/aprovacoes");
                    }
                } else {
                    avisar(colaborador, autor, SOLICITACAO_DECIDIDA,
                            autor.getNome() + " aprovou suas férias de " + ferias + ".", "/inicio");
                }
            }

            case CONFIRMADA_PELO_RH -> avisar(colaborador, autor, SOLICITACAO_DECIDIDA,
                    "O RH confirmou suas férias de " + ferias + ". Boas férias!", "/inicio");

            case RECUSADA -> avisar(colaborador, autor, SOLICITACAO_DECIDIDA, autor.getNome()
                    + " recusou suas férias de " + ferias + comMotivo(s.getMensagemDecisao()), "/inicio");
        }
    }

    // ================================================================== trocas

    @EventListener
    void aoMudarNegociacao(NegociacaoMudou evento) {
        Negociacao n = negociacaoRepository.findById(evento.negociacaoId()).orElseThrow();
        Colaborador autor = colaboradorRepository.findById(evento.autorId()).orElseThrow();
        Colaborador solicitante = n.getSolicitante();
        Colaborador colega = n.getColega();
        // "O outro": quem participa da troca e não é o autor da ação.
        Colaborador outro = mesmaPessoa(autor, solicitante) ? colega : solicitante;

        switch (evento.acao()) {
            case PROPOSTA -> avisar(colega, autor, TROCA_RECEBIDA,
                    solicitante.getNome() + " propôs trocar férias com você.", "/trocas");

            case CONTRAPROPOSTA -> avisar(outro, autor, TROCA_RECEBIDA,
                    autor.getNome() + " fez uma contraproposta na troca de férias.", "/trocas");

            case ACEITA -> {
                avisar(outro, autor, TROCA_ATUALIZADA,
                        autor.getNome() + " aceitou a troca de férias. Agora ela está com o gestor.", "/trocas");
                avisar(solicitante.getGestor(), autor, TROCA_PENDENTE, "A troca de férias entre "
                        + solicitante.getNome() + " e " + colega.getNome() + " espera a sua aprovação.", "/aprovacoes");
            }

            case RECUSADA -> avisar(outro, autor, TROCA_ATUALIZADA,
                    autor.getNome() + " recusou a troca de férias" + comMotivo(n.getMensagemDecisao()), "/trocas");

            case CANCELADA -> avisar(colega, autor, TROCA_ATUALIZADA,
                    solicitante.getNome() + " cancelou a proposta de troca de férias.", "/trocas");

            case APROVADA -> {
                var vigente = n.propostaVigente();
                avisar(solicitante, autor, TROCA_DECIDIDA, autor.getNome() + " aprovou a troca: suas férias agora são de "
                        + intervalo(vigente.getInicioSolicitante(), vigente.getFimSolicitante()) + ".", "/inicio");
                avisar(colega, autor, TROCA_DECIDIDA, autor.getNome() + " aprovou a troca: suas férias agora são de "
                        + intervalo(vigente.getInicioColega(), vigente.getFimColega()) + ".", "/inicio");
            }

            case RECUSADA_PELO_GESTOR -> {
                String texto = autor.getNome() + " recusou a troca de férias" + comMotivo(n.getMensagemDecisao());
                avisar(solicitante, autor, TROCA_DECIDIDA, texto, "/trocas");
                avisar(colega, autor, TROCA_DECIDIDA, texto, "/trocas");
            }
        }
    }

    // ================================================================== auxiliares

    /** Notifica, a menos que o destinatário não exista (ex.: sem gestor) ou seja o próprio autor. */
    private void avisar(Colaborador destinatario, Colaborador autor, TipoDeNotificacao tipo, String mensagem,
                        String link) {
        if (destinatario == null || mesmaPessoa(destinatario, autor)) {
            return;
        }
        notificacoes.criar(destinatario, tipo, mensagem, link);
    }

    private static boolean mesmaPessoa(Colaborador a, Colaborador b) {
        return a != null && b != null && Objects.equals(a.getId(), b.getId());
    }

    private static String intervalo(LocalDate inicio, LocalDate fim) {
        return inicio.format(DIA_MES) + " a " + fim.format(DIA_MES);
    }

    /** ": “motivo”." ou só "." */
    private static String comMotivo(String motivo) {
        return motivo == null || motivo.isBlank() ? "." : ": “" + motivo + "”.";
    }
}
