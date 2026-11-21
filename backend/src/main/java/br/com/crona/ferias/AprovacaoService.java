package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.colaborador.Perfil;
import br.com.crona.compartilhado.ReferenciaResposta;
import br.com.crona.compartilhado.erro.PedidoComImpedimentosException;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import br.com.crona.empresa.ConfiguracaoFerias;
import br.com.crona.empresa.ConfiguracaoFeriasRepository;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.ferias.SimulacaoService.Avaliacao;
import br.com.crona.ferias.aprovacao.DecisaoRequisicao;
import br.com.crona.ferias.aprovacao.ItemDeAprovacao;
import br.com.crona.ferias.aprovacao.ItemDeAprovacao.Situacao;
import br.com.crona.ferias.validacao.Verificacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * O lado do GESTOR e do RH: a fila de aprovação, aprovar, recusar e confirmar.
 *
 * Quem vê o quê:
 * - GESTOR: só os pedidos da própria equipe (colaboradores que têm ele como gestor);
 * - ADMIN (RH): os pedidos da empresa toda.
 *
 * Cada pedido é REAVALIADO na hora de aprovar, com a mesma avaliação da simulação: entre o
 * envio e a aprovação, outros pedidos podem ter sido aprovados e o cenário mudado.
 */
@Service
public class AprovacaoService {

    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final PeriodoAquisitivoRepository periodoRepository;
    private final ConfiguracaoFeriasRepository configuracaoRepository;
    private final SimulacaoService simulacaoService;
    private final EmpresaAtual empresaAtual;
    private final Clock relogio;

    public AprovacaoService(SolicitacaoFeriasRepository solicitacaoRepository,
                            PeriodoAquisitivoRepository periodoRepository,
                            ConfiguracaoFeriasRepository configuracaoRepository,
                            SimulacaoService simulacaoService,
                            EmpresaAtual empresaAtual,
                            Clock relogio) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.periodoRepository = periodoRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.simulacaoService = simulacaoService;
        this.empresaAtual = empresaAtual;
        this.relogio = relogio;
    }

    public enum Filtro { TODOS, COM_CONFLITO, SEM_CONFLITO }

    // ================================================================== GESTOR

    @Transactional(readOnly = true)
    public List<ItemDeAprovacao> fila(Filtro filtro) {
        var eu = simulacaoService.colaboradorLogado();
        return pendentesQueEuDecido(eu, StatusSolicitacao.AGUARDANDO_GESTOR).stream()
                .map(pedido -> paraItem(pedido, avaliar(pedido)))
                .filter(item -> switch (filtro) {
                    case TODOS -> true;
                    case SEM_CONFLITO -> item.situacao() == Situacao.SEM_CONFLITO;
                    case COM_CONFLITO -> item.situacao() != Situacao.SEM_CONFLITO;
                })
                .toList();
    }

    @Transactional(readOnly = true)
    public ItemDeAprovacao.Detalhe detalhe(Long id) {
        var pedido = pedidoQueEuPossoDecidir(id, simulacaoService.colaboradorLogado());
        var avaliacao = avaliar(pedido);
        return new ItemDeAprovacao.Detalhe(paraItem(pedido, avaliacao), avaliacao.periodo().saldo().livres(),
                avaliacao.resultado().verificacoes(), simulacaoService.paraResposta(avaliacao).colegasNoPeriodo());
    }

    @Transactional
    public SolicitacaoResposta aprovar(Long id, DecisaoRequisicao decisao) {
        var eu = simulacaoService.colaboradorLogado();
        var pedido = pedidoQueEuPossoDecidir(id, eu);
        periodoRepository.travarDoColaborador(pedido.getColaborador().getId());

        var resultado = avaliar(pedido).resultado();
        if (!resultado.podeEnviar()) {
            throw new PedidoComImpedimentosException(
                    "Este pedido não pode mais ser aprovado: o cenário mudou desde o envio.", resultado.verificacoes());
        }
        var atencoes = resultado.com(Verificacao.Resultado.ATENCAO);
        if (!atencoes.isEmpty()) {
            if (!decisao.mesmoAssim()) {
                throw new PedidoComImpedimentosException("O pedido tem " + atencoes.size()
                        + (atencoes.size() == 1 ? " ponto" : " pontos") + " de atenção. Para aprovar mesmo assim, "
                        + "confirme (mesmoAssim) e informe o motivo.", resultado.verificacoes());
            }
            if (decisao.mensagem() == null || decisao.mensagem().isBlank()) {
                throw new RegraVioladaException("Para aprovar mesmo assim, informe o motivo: ele fica registrado.");
            }
        }

        pedido.aprovarPeloGestor(eu, decisao.mensagem(), configuracao().isExigeConfirmacaoRh(), agora());
        return SolicitacaoResposta.de(pedido);
    }

    @Transactional
    public SolicitacaoResposta recusar(Long id, DecisaoRequisicao decisao) {
        var eu = simulacaoService.colaboradorLogado();
        var pedido = pedidoQueEuPossoDecidir(id, eu);
        pedido.recusar(eu, decisao.mensagem(), agora());
        return SolicitacaoResposta.de(pedido);
    }

    /** O botão "Aprovar os N sem conflito" do redesign. */
    @Transactional
    public List<SolicitacaoResposta> aprovarSemConflito() {
        var eu = simulacaoService.colaboradorLogado();
        boolean exigeRh = configuracao().isExigeConfirmacaoRh();
        List<SolicitacaoResposta> aprovados = new ArrayList<>();

        for (var pedido : pendentesQueEuDecido(eu, StatusSolicitacao.AGUARDANDO_GESTOR)) {
            if (pedido.getColaborador().getId().equals(eu.getId())) {
                continue;   // o próprio pedido do gestor fica de fora (ninguém aprova o próprio)
            }
            periodoRepository.travarDoColaborador(pedido.getColaborador().getId());
            // Reavalia um por um: aprovar o primeiro pode criar conflito para o segundo.
            if (situacao(avaliar(pedido)) == Situacao.SEM_CONFLITO) {
                pedido.aprovarPeloGestor(eu, null, exigeRh, agora());
                aprovados.add(SolicitacaoResposta.de(pedido));
            }
        }
        return aprovados;
    }

    // ================================================================== RH (EMP-06)

    @Transactional(readOnly = true)
    public List<ItemDeAprovacao> filaDoRh() {
        return solicitacaoRepository.findByStatusAndColaboradorEmpresaIdOrderByDataInicioAsc(
                        StatusSolicitacao.AGUARDANDO_RH, empresaAtual.id()).stream()
                .map(pedido -> paraItem(pedido, avaliar(pedido)))
                .toList();
    }

    @Transactional
    public SolicitacaoResposta confirmarNoRh(Long id) {
        var pedido = pedidoDaEmpresa(id);
        pedido.confirmarNoRh(simulacaoService.colaboradorLogado(), agora());
        return SolicitacaoResposta.de(pedido);
    }

    @Transactional
    public SolicitacaoResposta recusarNoRh(Long id, DecisaoRequisicao decisao) {
        var pedido = pedidoDaEmpresa(id);
        if (pedido.getStatus() != StatusSolicitacao.AGUARDANDO_RH) {
            throw new RegraVioladaException("Este pedido não está com o RH.");
        }
        pedido.recusar(simulacaoService.colaboradorLogado(), decisao.mensagem(), agora());
        return SolicitacaoResposta.de(pedido);
    }

    // ================================================================== auxiliares

    private List<SolicitacaoFerias> pendentesQueEuDecido(Colaborador eu, StatusSolicitacao status) {
        return eu.getPerfil() == Perfil.ADMIN
                ? solicitacaoRepository.findByStatusAndColaboradorEmpresaIdOrderByDataInicioAsc(status, empresaAtual.id())
                : solicitacaoRepository.findByStatusAndColaboradorGestorIdOrderByDataInicioAsc(status, eu.getId());
    }

    /** O pedido existe, é da empresa e é da minha equipe (ou sou ADMIN)? Senão, "não encontrado". */
    private SolicitacaoFerias pedidoQueEuPossoDecidir(Long id, Colaborador eu) {
        var pedido = pedidoDaEmpresa(id);
        var gestorDoColaborador = pedido.getColaborador().getGestor();
        boolean daMinhaEquipe = gestorDoColaborador != null && gestorDoColaborador.getId().equals(eu.getId());
        if (eu.getPerfil() != Perfil.ADMIN && !daMinhaEquipe) {
            throw new RecursoNaoEncontradoException("Pedido " + id + " não encontrado.");
        }
        return pedido;
    }

    private SolicitacaoFerias pedidoDaEmpresa(Long id) {
        return solicitacaoRepository.findByIdAndColaboradorEmpresaId(id, empresaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + id + " não encontrado."));
    }

    /** Reavalia um pedido existente com as regras de hoje, sem contar ele contra ele mesmo. */
    private Avaliacao avaliar(SolicitacaoFerias pedido) {
        var comoSimulacao = new SimulacaoRequisicao(pedido.getDataInicio(), pedido.getDiasGozo(),
                pedido.getDiasAbono(), pedido.getPeriodoAquisitivo().getNumero());
        return simulacaoService.avaliar(pedido.getColaborador(), comoSimulacao, pedido.getId());
    }

    private static Situacao situacao(Avaliacao avaliacao) {
        var resultado = avaliacao.resultado();
        if (!resultado.com(Verificacao.Resultado.IMPEDIMENTO).isEmpty()) return Situacao.COM_IMPEDIMENTO;
        if (!resultado.com(Verificacao.Resultado.ATENCAO).isEmpty()) return Situacao.COM_ATENCAO;
        return Situacao.SEM_CONFLITO;
    }

    private static ItemDeAprovacao paraItem(SolicitacaoFerias pedido, Avaliacao avaliacao) {
        var colaborador = pedido.getColaborador();
        var alertas = avaliacao.resultado().verificacoes().stream()
                .filter(v -> v.resultado() != Verificacao.Resultado.OK)
                .toList();
        return new ItemDeAprovacao(pedido.getId(), new ReferenciaResposta(colaborador.getId(), colaborador.getNome()),
                colaborador.getDepartamento() == null ? null : colaborador.getDepartamento().getNome(),
                pedido.getDataInicio(), pedido.getDataFim(), pedido.getDiasGozo(), pedido.getDiasAbono(),
                pedido.getPeriodoAquisitivo().getNumero(), pedido.getRecadoColaborador(), pedido.getCriadoEm(),
                situacao(avaliacao), alertas);
    }

    private ConfiguracaoFerias configuracao() {
        return configuracaoRepository.findByEmpresaId(empresaAtual.id()).orElseGet(ConfiguracaoFerias::padrao);
    }

    private Instant agora() {
        return Instant.now(relogio);
    }
}
