package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import br.com.crona.empresa.ConfiguracaoFerias;
import br.com.crona.empresa.ConfiguracaoFeriasRepository;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.ferias.PeriodoAquisitivoService.PeriodoComSaldo;
import br.com.crona.ferias.validacao.DadosDaEquipe;
import br.com.crona.ferias.validacao.DadosParaValidar;
import br.com.crona.ferias.validacao.ResultadoDaValidacao;
import br.com.crona.ferias.validacao.ValidadorDaEquipe;
import br.com.crona.ferias.validacao.ValidadorDeFerias;
import br.com.crona.ferias.validacao.Verificacao;
import br.com.crona.seguranca.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

/**
 * Simula um pedido de férias SEM gravar nada: busca no banco tudo o que as regras precisam,
 * chama os dois validadores de domínio e junta os resultados.
 *
 * A tela do calendário chama isto a cada data escolhida. Na Fase 5, o envio do pedido
 * vai usar exatamente a mesma simulação antes de gravar.
 */
@Service
public class SimulacaoService {

    private final PeriodoAquisitivoService periodoService;
    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final ConfiguracaoFeriasRepository configuracaoRepository;
    private final PeriodoRestritoRepository restritoRepository;
    private final FeriadoRepository feriadoRepository;
    private final EmpresaAtual empresaAtual;
    private final UsuarioAtual usuarioAtual;
    private final Clock relogio;

    public SimulacaoService(PeriodoAquisitivoService periodoService,
                            SolicitacaoFeriasRepository solicitacaoRepository,
                            ColaboradorRepository colaboradorRepository,
                            ConfiguracaoFeriasRepository configuracaoRepository,
                            PeriodoRestritoRepository restritoRepository,
                            FeriadoRepository feriadoRepository,
                            EmpresaAtual empresaAtual,
                            UsuarioAtual usuarioAtual,
                            Clock relogio) {
        this.periodoService = periodoService;
        this.solicitacaoRepository = solicitacaoRepository;
        this.colaboradorRepository = colaboradorRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.restritoRepository = restritoRepository;
        this.feriadoRepository = feriadoRepository;
        this.empresaAtual = empresaAtual;
        this.usuarioAtual = usuarioAtual;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public SimulacaoResposta simular(SimulacaoRequisicao pedido) {
        Long empresaId = empresaAtual.id();
        Colaborador colaborador = colaboradorRepository.findByIdAndEmpresaId(usuarioAtual.id(), empresaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador não encontrado."));
        LocalDate hoje = LocalDate.now(relogio);
        var config = configuracaoRepository.findByEmpresaId(empresaId).orElseGet(ConfiguracaoFerias::padrao);

        // 1. Regras do próprio pedido (CLT)
        var escolhido = escolherPeriodo(colaborador.getId(), pedido.periodo());
        var resultadoIndividual = ValidadorDeFerias.validar(dadosDoPedido(pedido, escolhido, hoje, empresaId, config));

        // 2. Regras da equipe e da empresa (EMP), nas datas já calculadas
        var resultadoEquipe = ValidadorDaEquipe.validar(
                dadosDaEquipe(colaborador, empresaId, config, resultadoIndividual.inicio(), resultadoIndividual.fim()));

        // 3. Um checklist só, com as duas listas juntas
        var resultado = new ResultadoDaValidacao(resultadoIndividual.inicio(), resultadoIndividual.fim(),
                resultadoIndividual.voltaAoTrabalho(),
                Stream.concat(resultadoIndividual.verificacoes().stream(), resultadoEquipe.verificacoes().stream()).toList());

        return new SimulacaoResposta(resultado.inicio(), resultado.fim(), resultado.voltaAoTrabalho(),
                escolhido.periodo().getNumero(), escolhido.saldo().livres(), resultado.podeEnviar(), resumo(resultado),
                resultado.verificacoes(),
                resultadoEquipe.colegasNoPeriodo().stream()
                        .map(a -> new SimulacaoResposta.ColegaFora(a.nome(), a.inicio(), a.fim(), a.pendente()))
                        .toList());
    }

    /** O período informado, ou o mais antigo que já pode ser usado e ainda tem dias livres. */
    private PeriodoComSaldo escolherPeriodo(Long colaboradorId, Integer numero) {
        var periodos = periodoService.periodosComSaldo(colaboradorId);
        if (periodos.isEmpty()) {
            throw new RegraVioladaException("Você ainda não tem período aquisitivo.");
        }
        if (numero != null) {
            return periodos.stream().filter(p -> p.periodo().getNumero() == numero).findFirst()
                    .orElseThrow(() -> new RecursoNaoEncontradoException("Período aquisitivo " + numero + " não encontrado."));
        }
        return periodos.stream()
                .filter(p -> p.saldo().situacao() != SaldoDoPeriodo.Situacao.EM_AQUISICAO && p.saldo().livres() > 0)
                .findFirst()
                .orElse(periodos.getLast());     // nenhum disponível: o validador vai explicar o motivo
    }

    private DadosParaValidar dadosDoPedido(SimulacaoRequisicao pedido, PeriodoComSaldo escolhido, LocalDate hoje,
                                           Long empresaId, ConfiguracaoFerias config) {
        var periodo = escolhido.periodo();
        var outrosPedidos = solicitacaoRepository.findByPeriodoAquisitivoIdAndStatusIn(
                periodo.getId(), StatusSolicitacao.EM_ANDAMENTO_OU_APROVADA);

        // Feriados do início até um pouco depois do fim (para calcular a volta ao trabalho).
        LocalDate ateQuando = pedido.inicio().plusDays(pedido.diasGozo() + 10L);
        var feriados = new HashSet<>(feriadoRepository.datasEntre(empresaId, pedido.inicio(), ateQuando));

        return new DadosParaValidar(
                new DadosParaValidar.Pedido(pedido.inicio(), pedido.diasGozo(), pedido.diasAbono()),
                hoje,
                new DadosParaValidar.Periodo(periodo.getNumero(), periodo.getDataFim(), periodo.getDataLimiteConcessao(),
                        escolhido.saldo().direito(), escolhido.saldo().livres(), escolhido.saldo().situacao()),
                outrosPedidos.stream().map(SolicitacaoFerias::getDiasGozo).toList(),
                outrosPedidos.stream().mapToInt(SolicitacaoFerias::getDiasAbono).sum(),
                feriados,
                config.getAntecedenciaMinimaDias());
    }

    private DadosDaEquipe dadosDaEquipe(Colaborador colaborador, Long empresaId, ConfiguracaoFerias config,
                                        LocalDate inicio, LocalDate fim) {
        var ausencias = solicitacaoRepository.ausenciasDosColegas(
                empresaId, colaborador.getId(), StatusSolicitacao.EM_ANDAMENTO_OU_APROVADA, inicio, fim);
        var departamento = colaborador.getDepartamento();
        Long departamentoId = departamento == null ? null : departamento.getId();

        DadosDaEquipe.Departamento dadosDoDepartamento = departamento == null ? null
                : new DadosDaEquipe.Departamento(departamento.getNome(),
                (int) colaboradorRepository.countByDepartamentoIdAndAtivoTrue(departamentoId),
                config.getPercentualMinimoTrabalhando());

        return new DadosDaEquipe(inicio, fim, dadosDoDepartamento,
                ausencias.stream().filter(a -> Objects.equals(a.departamentoId(), departamentoId) && departamentoId != null)
                        .map(SimulacaoService::paraAusencia).toList(),
                config.getLimiteFeriasSimultaneasEmpresa(),
                ausencias.stream().map(SimulacaoService::paraAusencia).toList(),
                restritoRepository.queEncostam(empresaId, departamentoId, inicio, fim).stream()
                        .map(r -> new DadosDaEquipe.Restricao(r.getMotivo(), r.getTipo() == PeriodoRestrito.Tipo.BLOQUEIO,
                                r.getDataInicio(), r.getDataFim()))
                        .toList());
    }

    private static DadosDaEquipe.Ausencia paraAusencia(AusenciaRegistrada a) {
        return new DadosDaEquipe.Ausencia(a.nome(), a.inicio(), a.fim(), a.status() != StatusSolicitacao.APROVADA);
    }

    /** A frase do resumo, como no redesign. */
    private static String resumo(ResultadoDaValidacao resultado) {
        int impedimentos = resultado.com(Verificacao.Resultado.IMPEDIMENTO).size();
        int atencoes = resultado.com(Verificacao.Resultado.ATENCAO).size();
        if (impedimentos > 0) {
            return impedimentos == 1 ? "1 impedimento: ajuste as datas para poder enviar."
                    : impedimentos + " impedimentos: ajuste as datas para poder enviar.";
        }
        if (atencoes > 0) {
            return atencoes == 1 ? "1 ponto de atenção: o gestor verá isso."
                    : atencoes + " pontos de atenção: o gestor verá isso.";
        }
        return "Tudo certo para enviar.";
    }
}
