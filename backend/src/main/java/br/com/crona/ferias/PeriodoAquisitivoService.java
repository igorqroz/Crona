package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.ferias.SaldoDoPeriodo.Consumo;
import br.com.crona.seguranca.UsuarioAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Períodos aquisitivos: gera a partir da admissão, recebe as faltas e calcula o saldo.
 * As REGRAS ficam nas classes de domínio (CalendarioAquisitivo, SaldoDoPeriodo); este
 * service só busca os dados, chama as regras e grava.
 */
@Service
public class PeriodoAquisitivoService {

    private final PeriodoAquisitivoRepository periodoRepository;
    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final EmpresaAtual empresaAtual;
    private final UsuarioAtual usuarioAtual;
    private final Clock relogio;

    public PeriodoAquisitivoService(PeriodoAquisitivoRepository periodoRepository,
                                    SolicitacaoFeriasRepository solicitacaoRepository,
                                    ColaboradorRepository colaboradorRepository,
                                    EmpresaAtual empresaAtual,
                                    UsuarioAtual usuarioAtual,
                                    Clock relogio) {
        this.periodoRepository = periodoRepository;
        this.solicitacaoRepository = solicitacaoRepository;
        this.colaboradorRepository = colaboradorRepository;
        this.empresaAtual = empresaAtual;
        this.usuarioAtual = usuarioAtual;
        this.relogio = relogio;
    }

    // ------------------------------------------------------------------ consultas

    @Transactional(readOnly = true)
    public List<PeriodoAquisitivoResposta> doColaborador(Long colaboradorId) {
        return comSaldo(colaboradorDaEmpresa(colaboradorId).getId());
    }

    /** Os períodos de quem está logado. Cada um só vê os seus. */
    @Transactional(readOnly = true)
    public List<PeriodoAquisitivoResposta> meus() {
        return comSaldo(colaboradorDaEmpresa(usuarioAtual.id()).getId());
    }

    // ------------------------------------------------------------------ faltas (CLT-02)

    @Transactional
    public PeriodoAquisitivoResposta registrarFaltas(Long colaboradorId, int numero, int faltas) {
        colaboradorDaEmpresa(colaboradorId);
        var periodo = periodoRepository.findByColaboradorIdAndNumero(colaboradorId, numero)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Período aquisitivo " + numero + " não encontrado."));

        int novoDireito = CalendarioAquisitivo.diasDeDireito(faltas);
        var saldoAtual = saldo(periodo, consumosPorPeriodo(colaboradorId));
        int jaUsados = saldoAtual.aprovados() + saldoAtual.emPedido();
        if (novoDireito < jaUsados) {
            throw new RegraVioladaException("Com " + faltas + " faltas o direito cai para " + novoDireito
                    + " dias, mas o colaborador já tem " + jaUsados + " dias aprovados ou pedidos neste período.");
        }

        periodo.registrarFaltas(faltas);
        return PeriodoAquisitivoResposta.de(periodo, saldo(periodo, consumosPorPeriodo(colaboradorId)));
    }

    // ------------------------------------------------------------------ sincronização

    /**
     * Deixa os períodos do colaborador iguais ao calendário: cria os que faltam e ajusta as
     * datas se a admissão mudou. Pode rodar quantas vezes quiser (é "idempotente").
     */
    @Transactional
    public void sincronizar(Long colaboradorId) {
        var colaborador = colaboradorRepository.findById(colaboradorId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador " + colaboradorId + " não encontrado."));
        sincronizar(colaborador);
    }

    @Transactional
    public void sincronizarTodos() {
        colaboradorRepository.findAll().forEach(this::sincronizar);
    }

    private void sincronizar(Colaborador colaborador) {
        var calculados = CalendarioAquisitivo.periodos(colaborador.getDataAdmissao(), hoje());
        Map<Integer, PeriodoAquisitivo> existentes = periodoRepository
                .findByColaboradorIdOrderByNumeroAsc(colaborador.getId()).stream()
                .collect(Collectors.toMap(PeriodoAquisitivo::getNumero, Function.identity()));

        for (var calculado : calculados) {
            var existente = existentes.remove(calculado.numero());
            if (existente != null) {
                existente.ajustarDatas(calculado);
            } else {
                periodoRepository.save(new PeriodoAquisitivo(colaborador, calculado));
            }
        }

        // Sobrou período que não existe mais (a admissão foi corrigida para uma data mais recente).
        for (var sobrando : existentes.values()) {
            if (solicitacaoRepository.existsByPeriodoAquisitivoId(sobrando.getId())) {
                throw new RegraVioladaException("O período aquisitivo " + sobrando.getNumero()
                        + " já tem pedidos de férias. A data de admissão não pode ir para depois dele.");
            }
            periodoRepository.delete(sobrando);
        }
    }

    // ------------------------------------------------------------------ auxiliares

    /** Um período com o saldo calculado, para uso dentro do pacote (ex.: a simulação). */
    record PeriodoComSaldo(PeriodoAquisitivo periodo, SaldoDoPeriodo saldo) {
    }

    @Transactional(readOnly = true)
    List<PeriodoComSaldo> periodosComSaldo(Long colaboradorId) {
        return periodosComSaldo(colaboradorId, null);
    }

    /**
     * @param ignorarSolicitacaoId um pedido para NÃO contar no saldo. Ao reavaliar um pedido que já
     *                             existe (ex.: na aprovação), os dias dele não podem contar duas vezes.
     */
    @Transactional(readOnly = true)
    List<PeriodoComSaldo> periodosComSaldo(Long colaboradorId, Long ignorarSolicitacaoId) {
        var consumos = consumosPorPeriodo(colaboradorId, ignorarSolicitacaoId);
        return periodoRepository.findByColaboradorIdOrderByNumeroAsc(colaboradorId).stream()
                .map(periodo -> new PeriodoComSaldo(periodo, saldo(periodo, consumos)))
                .toList();
    }

    private List<PeriodoAquisitivoResposta> comSaldo(Long colaboradorId) {
        return periodosComSaldo(colaboradorId).stream()
                .map(p -> PeriodoAquisitivoResposta.de(p.periodo(), p.saldo()))
                .toList();
    }

    private SaldoDoPeriodo saldo(PeriodoAquisitivo periodo, Map<Long, List<Consumo>> consumos) {
        return SaldoDoPeriodo.calcular(periodo.getDataFim(), periodo.getDataLimiteConcessao(),
                periodo.getDiasDireito(), consumos.getOrDefault(periodo.getId(), List.of()), hoje());
    }

    /** Uma consulta só para todos os pedidos do colaborador, agrupados por período. */
    private Map<Long, List<Consumo>> consumosPorPeriodo(Long colaboradorId) {
        return consumosPorPeriodo(colaboradorId, null);
    }

    private Map<Long, List<Consumo>> consumosPorPeriodo(Long colaboradorId, Long ignorarSolicitacaoId) {
        return solicitacaoRepository.consumosDoColaborador(colaboradorId).stream()
                .filter(c -> !c.solicitacaoId().equals(ignorarSolicitacaoId))
                .collect(Collectors.groupingBy(ConsumoPorPeriodo::periodoId,
                        Collectors.mapping(c -> new Consumo(c.dias(), c.status()), Collectors.toList())));
    }

    private Colaborador colaboradorDaEmpresa(Long colaboradorId) {
        return colaboradorRepository.findByIdAndEmpresaId(colaboradorId, empresaAtual.id())
                .orElseThrow(() -> new RecursoNaoEncontradoException("Colaborador " + colaboradorId + " não encontrado."));
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio);
    }
}
