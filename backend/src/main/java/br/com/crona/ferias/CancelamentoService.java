package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.compartilhado.ReferenciaResposta;
import br.com.crona.compartilhado.erro.ConflitoException;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.ferias.aprovacao.CancelamentoRequisicao;
import br.com.crona.ferias.aprovacao.FeriasAprovada;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

/**
 * O RH cancela férias que já estavam aprovadas, para corrigir um erro humano.
 *
 * As regras do próprio pedido (só APROVADA, até a véspera, motivo obrigatório, ninguém cancela
 * as próprias férias) ficam em {@link SolicitacaoFerias#cancelarAprovada}. Aqui fica o que
 * depende de outras tabelas: a troca em aberto e a notificação.
 */
@Service
public class CancelamentoService {

    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final NegociacaoRepository negociacaoRepository;
    private final SimulacaoService simulacaoService;
    private final EmpresaAtual empresaAtual;
    private final Clock relogio;
    private final ApplicationEventPublisher eventos;

    public CancelamentoService(SolicitacaoFeriasRepository solicitacaoRepository,
                               NegociacaoRepository negociacaoRepository,
                               SimulacaoService simulacaoService,
                               EmpresaAtual empresaAtual,
                               Clock relogio,
                               ApplicationEventPublisher eventos) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.negociacaoRepository = negociacaoRepository;
        this.simulacaoService = simulacaoService;
        this.empresaAtual = empresaAtual;
        this.relogio = relogio;
        this.eventos = eventos;
    }

    /** As férias aprovadas da empresa que ainda não começaram: as únicas que ainda dá para cancelar. */
    @Transactional(readOnly = true)
    public List<FeriasAprovada> aprovadasQueAindaVaoComecar() {
        var eu = simulacaoService.colaboradorLogado();
        return solicitacaoRepository.aprovadasQueComecamDepoisDe(empresaAtual.id(), hoje()).stream()
                .map(pedido -> paraItem(pedido, eu))
                .toList();
    }

    @Transactional
    public SolicitacaoResposta cancelar(Long id, CancelamentoRequisicao requisicao) {
        var rh = simulacaoService.colaboradorLogado();
        var pedido = solicitacaoRepository.findByIdAndColaboradorEmpresaId(id, empresaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + id + " não encontrado."));

        if (emTrocaAberta(pedido)) {
            throw new ConflitoException(MENSAGEM_TROCA_ABERTA);
        }
        pedido.cancelarAprovada(rh, requisicao.motivo(), hoje(), Instant.now(relogio));

        // Avisa o colaborador e o gestor (ver Notificador). Os dias voltam sozinhos para o saldo:
        // o saldo é calculado a partir dos pedidos que ainda consomem dias, e CANCELADA não consome.
        eventos.publishEvent(new SolicitacaoMudou(pedido.getId(), SolicitacaoMudou.Acao.APROVADA_CANCELADA_PELO_RH,
                rh.getId()));
        return SolicitacaoResposta.de(pedido);
    }

    // ------------------------------------------------------------------ auxiliares

    private static final String MENSAGEM_TROCA_ABERTA =
            "Estas férias estão numa troca em aberto. Encerre a troca antes de cancelar.";

    private FeriasAprovada paraItem(SolicitacaoFerias pedido, Colaborador eu) {
        var colaborador = pedido.getColaborador();
        var aprovou = pedido.getRhConfirmouPor() != null ? pedido.getRhConfirmouPor() : pedido.getGestorDecidiuPor();
        String bloqueio = null;
        if (colaborador.getId().equals(eu.getId())) {
            bloqueio = "São as suas férias: outra pessoa do RH precisa cancelar.";
        } else if (emTrocaAberta(pedido)) {
            bloqueio = MENSAGEM_TROCA_ABERTA;
        }
        return new FeriasAprovada(pedido.getId(),
                new ReferenciaResposta(colaborador.getId(), colaborador.getNome()),
                colaborador.getDepartamento() == null ? null : colaborador.getDepartamento().getNome(),
                pedido.getDataInicio(), pedido.getDataFim(), pedido.getDiasGozo(), pedido.getDiasAbono(),
                aprovou == null ? null : new ReferenciaResposta(aprovou.getId(), aprovou.getNome()),
                bloqueio);
    }

    private boolean emTrocaAberta(SolicitacaoFerias pedido) {
        return negociacaoRepository.pedidoEmNegociacao(pedido.getId(), StatusNegociacao.EM_ABERTO);
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }
}
