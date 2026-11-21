package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.colaborador.Perfil;
import br.com.crona.compartilhado.ReferenciaResposta;
import br.com.crona.compartilhado.erro.ConflitoException;
import br.com.crona.compartilhado.erro.PedidoComImpedimentosException;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import br.com.crona.empresa.ConfiguracaoFerias;
import br.com.crona.empresa.ConfiguracaoFeriasRepository;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.ferias.SimulacaoService.Avaliacao;
import br.com.crona.ferias.SimulacaoService.CenarioDaTroca;
import br.com.crona.ferias.aprovacao.DecisaoRequisicao;
import br.com.crona.ferias.aprovacao.ItemDeAprovacao.Situacao;
import br.com.crona.ferias.negociacao.ContrapropostaRequisicao;
import br.com.crona.ferias.negociacao.FeriasDoColega;
import br.com.crona.ferias.negociacao.NegociacaoResposta;
import br.com.crona.ferias.negociacao.PropostaDeTrocaRequisicao;
import br.com.crona.ferias.negociacao.TrocaParaAprovar;
import br.com.crona.ferias.validacao.Verificacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A negociação de troca (Fase 6), dos dois lados:
 * - os COLEGAS: ver as férias da equipe, propor, contrapropor, aceitar, recusar e cancelar;
 * - o GESTOR: a fila de trocas da equipe, aprovar e recusar.
 *
 * As datas propostas passam pela MESMA avaliação da simulação (Fase 4), uma vez para cada lado,
 * no cenário depois da troca. Impedimento bloqueia a proposta; ponto de atenção vai para o gestor.
 */
@Service
public class NegociacaoService {

    private final NegociacaoRepository negociacaoRepository;
    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final PeriodoAquisitivoRepository periodoRepository;
    private final ConfiguracaoFeriasRepository configuracaoRepository;
    private final SimulacaoService simulacaoService;
    private final EmpresaAtual empresaAtual;
    private final Clock relogio;

    public NegociacaoService(NegociacaoRepository negociacaoRepository,
                             SolicitacaoFeriasRepository solicitacaoRepository,
                             PeriodoAquisitivoRepository periodoRepository,
                             ConfiguracaoFeriasRepository configuracaoRepository,
                             SimulacaoService simulacaoService,
                             EmpresaAtual empresaAtual,
                             Clock relogio) {
        this.negociacaoRepository = negociacaoRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.periodoRepository = periodoRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.simulacaoService = simulacaoService;
        this.empresaAtual = empresaAtual;
        this.relogio = relogio;
    }

    // ================================================================== COLEGAS

    /** As férias aprovadas dos colegas da minha equipe que ainda vão começar: o que dá para propor trocar. */
    @Transactional(readOnly = true)
    public List<FeriasDoColega> feriasDaEquipe() {
        var eu = simulacaoService.colaboradorLogado();
        if (eu.getGestor() == null) {
            return List.of();
        }
        return solicitacaoRepository.feriasDaEquipe(eu.getGestor().getId(), eu.getId(), StatusSolicitacao.APROVADA, hoje())
                .stream()
                .map(s -> new FeriasDoColega(s.getId(),
                        new ReferenciaResposta(s.getColaborador().getId(), s.getColaborador().getNome()),
                        s.getDataInicio(), s.getDataFim(), s.getDiasGozo()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<NegociacaoResposta> minhas() {
        var eu = simulacaoService.colaboradorLogado();
        return negociacaoRepository.minhas(eu.getId()).stream().map(NegociacaoResposta::de).toList();
    }

    @Transactional
    public NegociacaoResposta propor(PropostaDeTrocaRequisicao proposta) {
        var eu = simulacaoService.colaboradorLogado();
        var pedidoDoColega = pedidoDaEmpresa(proposta.pedidoDoColegaId());
        var meuPedido = proposta.pedidoDoSolicitanteId() == null ? null : pedidoDaEmpresa(proposta.pedidoDoSolicitanteId());

        for (var pedido : Stream.of(pedidoDoColega, meuPedido).filter(p -> p != null).toList()) {
            if (!pedido.getDataInicio().isAfter(hoje())) {
                throw new RegraVioladaException("Férias que já começaram não entram numa troca.");
            }
            if (negociacaoRepository.pedidoEmNegociacao(pedido.getId(), StatusNegociacao.EM_ABERTO)) {
                throw new ConflitoException("As férias de " + pedido.getColaborador().getNome()
                        + " já estão em outra negociação em aberto.");
            }
        }

        // As regras da troca em si (aprovadas, mesma equipe, mesmos dias) ficam na entidade.
        var negociacao = new Negociacao(eu, pedidoDoColega, meuPedido, proposta.inicioSolicitante(),
                proposta.diasSolicitante(), proposta.inicioColega(), proposta.mensagem());
        exigirSemImpedimentos(avaliar(negociacao));
        return NegociacaoResposta.de(negociacaoRepository.save(negociacao));
    }

    @Transactional
    public NegociacaoResposta contrapropor(Long id, ContrapropostaRequisicao contraproposta) {
        var eu = simulacaoService.colaboradorLogado();
        var negociacao = negociacaoDeQueParticipo(id, eu);
        negociacao.contrapropor(eu, contraproposta.inicioSolicitante(), contraproposta.inicioColega(),
                contraproposta.mensagem());
        exigirSemImpedimentos(avaliar(negociacao));
        return NegociacaoResposta.de(negociacao);
    }

    @Transactional
    public NegociacaoResposta aceitar(Long id) {
        var eu = simulacaoService.colaboradorLogado();
        var negociacao = negociacaoDeQueParticipo(id, eu);
        negociacao.aceitar(eu);
        return NegociacaoResposta.de(negociacao);
    }

    @Transactional
    public NegociacaoResposta recusar(Long id, DecisaoRequisicao decisao) {
        var eu = simulacaoService.colaboradorLogado();
        var negociacao = negociacaoDeQueParticipo(id, eu);
        negociacao.recusar(eu, decisao.mensagem(), agora());
        return NegociacaoResposta.de(negociacao);
    }

    @Transactional
    public NegociacaoResposta cancelar(Long id) {
        var eu = simulacaoService.colaboradorLogado();
        var negociacao = negociacaoDeQueParticipo(id, eu);
        negociacao.cancelar(eu);
        return NegociacaoResposta.de(negociacao);
    }

    // ================================================================== GESTOR

    @Transactional(readOnly = true)
    public List<TrocaParaAprovar> filaDoGestor() {
        var eu = simulacaoService.colaboradorLogado();
        var pendentes = eu.getPerfil() == Perfil.ADMIN
                ? negociacaoRepository.findByStatusAndSolicitanteEmpresaIdOrderByIdAsc(
                        StatusNegociacao.AGUARDANDO_GESTOR, empresaAtual.id())
                : negociacaoRepository.findByStatusAndSolicitanteGestorIdOrderByIdAsc(
                        StatusNegociacao.AGUARDANDO_GESTOR, eu.getId());
        return pendentes.stream().map(n -> paraAprovar(n, avaliar(n))).toList();
    }

    @Transactional(readOnly = true)
    public TrocaParaAprovar detalheParaOGestor(Long id) {
        var negociacao = trocaQueEuPossoDecidir(id, simulacaoService.colaboradorLogado());
        return paraAprovar(negociacao, avaliar(negociacao));
    }

    /**
     * Aprova a troca e aplica as datas novas nas férias dos dois.
     * Reavalia antes, como na aprovação de pedido: o cenário pode ter mudado desde a proposta.
     */
    @Transactional
    public NegociacaoResposta aprovar(Long id, DecisaoRequisicao decisao) {
        var eu = simulacaoService.colaboradorLogado();
        var negociacao = trocaQueEuPossoDecidir(id, eu);

        // Trava os períodos dos dois, sempre na mesma ordem (do menor id para o maior). Se duas
        // transações travassem em ordens diferentes, uma poderia esperar a outra para sempre (deadlock).
        Stream.of(negociacao.getSolicitante().getId(), negociacao.getColega().getId()).sorted()
                .forEach(periodoRepository::travarDoColaborador);

        var avaliacao = avaliar(negociacao);
        AprovacaoService.exigirQuePossaAprovar(avaliacao.verificacoesDosDois(), decisao, "Esta troca");

        negociacao.aprovarPeloGestor(eu, decisao.mensagem(), agora());
        aplicarAsDatasNovas(negociacao, avaliacao, eu);
        return NegociacaoResposta.de(negociacao);
    }

    @Transactional
    public NegociacaoResposta recusarPeloGestor(Long id, DecisaoRequisicao decisao) {
        var eu = simulacaoService.colaboradorLogado();
        var negociacao = trocaQueEuPossoDecidir(id, eu);
        negociacao.recusarPeloGestor(eu, decisao.mensagem(), agora());
        return NegociacaoResposta.de(negociacao);
    }

    // ================================================================== avaliação da troca

    /** O resultado das duas avaliações: as datas novas de quem propôs e as do colega. */
    record AvaliacaoDaTroca(Avaliacao solicitante, Avaliacao colega, String nomeSolicitante, String nomeColega) {

        /** As duas listas numa só, com o nome de cada um na frente da mensagem (para o 422 e a tela). */
        List<Verificacao> verificacoesDosDois() {
            return Stream.concat(
                    comNome(nomeSolicitante, solicitante.resultado().verificacoes()),
                    comNome(nomeColega, colega.resultado().verificacoes())).toList();
        }

        private static Stream<Verificacao> comNome(String nome, List<Verificacao> verificacoes) {
            return verificacoes.stream().map(v -> new Verificacao(v.regra(), v.resultado(), nome + ": " + v.mensagem()));
        }
    }

    /**
     * Avalia a proposta vigente para os dois lados, no cenário depois da troca:
     * as férias antigas dos dois saem da conta, e as datas novas do outro lado entram.
     */
    private AvaliacaoDaTroca avaliar(Negociacao negociacao) {
        var proposta = negociacao.propostaVigente();
        var solicitante = negociacao.getSolicitante();
        var colega = negociacao.getColega();

        Set<Long> pedidosQueSaem = new HashSet<>();
        pedidosQueSaem.add(negociacao.getPedidoDoColega().getId());
        if (negociacao.getPedidoDoSolicitante() != null) {
            pedidosQueSaem.add(negociacao.getPedidoDoSolicitante().getId());
        }

        var ladoDoSolicitante = avaliarUmLado(solicitante, negociacao.getPedidoDoSolicitante(),
                proposta.getInicioSolicitante(), negociacao.diasDoSolicitante(),
                new CenarioDaTroca(pedidosQueSaem, List.of(
                        ausencia(colega, proposta.getInicioColega(), proposta.getFimColega()))));
        var ladoDoColega = avaliarUmLado(colega, negociacao.getPedidoDoColega(),
                proposta.getInicioColega(), negociacao.getPedidoDoColega().getDiasGozo(),
                new CenarioDaTroca(pedidosQueSaem, List.of(
                        ausencia(solicitante, proposta.getInicioSolicitante(), proposta.getFimSolicitante()))));

        return new AvaliacaoDaTroca(ladoDoSolicitante, ladoDoColega, solicitante.getNome(), colega.getNome());
    }

    private Avaliacao avaliarUmLado(Colaborador quem, SolicitacaoFerias pedidoAtual, LocalDate inicio, int dias,
                                    CenarioDaTroca cenario) {
        // Com férias marcadas, elas continuam no mesmo período aquisitivo e com o mesmo abono.
        var comoSimulacao = pedidoAtual == null
                ? new SimulacaoRequisicao(inicio, dias, 0, null)
                : new SimulacaoRequisicao(inicio, dias, pedidoAtual.getDiasAbono(),
                pedidoAtual.getPeriodoAquisitivo().getNumero());
        return simulacaoService.avaliar(quem, comoSimulacao, pedidoAtual == null ? null : pedidoAtual.getId(), cenario);
    }

    /** As datas propostas para alguém, no formato de "colega fora" que a avaliação da equipe usa. */
    private static AusenciaRegistrada ausencia(Colaborador quem, LocalDate inicio, LocalDate fim) {
        var departamento = quem.getDepartamento();
        return new AusenciaRegistrada(null, quem.getNome(), inicio, fim, StatusSolicitacao.AGUARDANDO_GESTOR,
                departamento == null ? null : departamento.getId());
    }

    private static void exigirSemImpedimentos(AvaliacaoDaTroca avaliacao) {
        var verificacoes = avaliacao.verificacoesDosDois();
        long impedimentos = verificacoes.stream()
                .filter(v -> v.resultado() == Verificacao.Resultado.IMPEDIMENTO).count();
        if (impedimentos > 0) {
            throw new PedidoComImpedimentosException("As datas propostas têm " + impedimentos
                    + (impedimentos == 1 ? " impedimento" : " impedimentos") + ". Ajuste e tente de novo.",
                    verificacoes);
        }
    }

    private static TrocaParaAprovar paraAprovar(Negociacao negociacao, AvaliacaoDaTroca avaliacao) {
        var todas = avaliacao.verificacoesDosDois();
        Situacao situacao;
        if (todas.stream().anyMatch(v -> v.resultado() == Verificacao.Resultado.IMPEDIMENTO)) {
            situacao = Situacao.COM_IMPEDIMENTO;
        } else if (todas.stream().anyMatch(v -> v.resultado() == Verificacao.Resultado.ATENCAO)) {
            situacao = Situacao.COM_ATENCAO;
        } else {
            situacao = Situacao.SEM_CONFLITO;
        }
        return new TrocaParaAprovar(NegociacaoResposta.de(negociacao), situacao,
                avaliacao.solicitante().resultado().verificacoes(), avaliacao.colega().resultado().verificacoes());
    }

    // ================================================================== aplicar a troca

    private void aplicarAsDatasNovas(Negociacao negociacao, AvaliacaoDaTroca avaliacao, Colaborador gestor) {
        var proposta = negociacao.propostaVigente();
        negociacao.getPedidoDoColega().remarcarPorTroca(proposta.getInicioColega(), proposta.getFimColega());

        if (negociacao.getPedidoDoSolicitante() != null) {
            negociacao.getPedidoDoSolicitante().remarcarPorTroca(proposta.getInicioSolicitante(),
                    proposta.getFimSolicitante());
            return;
        }

        // Quem propôs não tinha férias marcadas: o pedido dele nasce agora, já aprovado pelo gestor
        // (ou esperando o RH, se a EMP-06 estiver ligada, como qualquer pedido novo).
        var novo = new SolicitacaoFerias(negociacao.getSolicitante(), avaliacao.solicitante().periodo().periodo(),
                proposta.getInicioSolicitante(), proposta.getFimSolicitante(), negociacao.diasDoSolicitante(), 0,
                "Troca de férias com " + negociacao.getColega().getNome());
        novo.aprovarPeloGestor(gestor, "Troca aprovada", configuracao().isExigeConfirmacaoRh(), agora());
        negociacao.vincularPedidoNovoDoSolicitante(solicitacaoRepository.save(novo));
    }

    // ================================================================== auxiliares

    private SolicitacaoFerias pedidoDaEmpresa(Long id) {
        return solicitacaoRepository.findByIdAndColaboradorEmpresaId(id, empresaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + id + " não encontrado."));
    }

    private Negociacao negociacaoDaEmpresa(Long id) {
        return negociacaoRepository.findByIdAndSolicitanteEmpresaId(id, empresaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Negociação " + id + " não encontrada."));
    }

    /** Quem não participa recebe "não encontrada", e não "proibido" (não revela que ela existe). */
    private Negociacao negociacaoDeQueParticipo(Long id, Colaborador eu) {
        var negociacao = negociacaoDaEmpresa(id);
        if (!negociacao.participa(eu)) {
            throw new RecursoNaoEncontradoException("Negociação " + id + " não encontrada.");
        }
        return negociacao;
    }

    /** A troca é da minha equipe (os dois têm a mim como gestor) ou sou ADMIN? */
    private Negociacao trocaQueEuPossoDecidir(Long id, Colaborador eu) {
        var negociacao = negociacaoDaEmpresa(id);
        var gestor = negociacao.getSolicitante().getGestor();
        boolean daMinhaEquipe = gestor != null && gestor.getId().equals(eu.getId());
        if (eu.getPerfil() != Perfil.ADMIN && !daMinhaEquipe) {
            throw new RecursoNaoEncontradoException("Negociação " + id + " não encontrada.");
        }
        return negociacao;
    }

    private ConfiguracaoFerias configuracao() {
        return configuracaoRepository.findByEmpresaId(empresaAtual.id()).orElseGet(ConfiguracaoFerias::padrao);
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }

    private Instant agora() {
        return Instant.now(relogio);
    }
}
