package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.colaborador.Perfil;
import br.com.crona.compartilhado.erro.ParametroInvalidoException;
import br.com.crona.departamento.Departamento;
import br.com.crona.empresa.EmpresaAtual;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fase 8c: o calendário "quem está fora" de várias pessoas e departamentos.
 *
 * Quem vê o quê (como na fila de aprovação):
 * - GESTOR: só a própria equipe (quem tem ele como gestor);
 * - ADMIN (RH): a empresa toda.
 */
@Service
public class CalendarioDaGestaoService {

    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final PeriodoRestritoRepository restritoRepository;
    private final FeriadoRepository feriadoRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final CalendarioService calendarioService;
    private final SimulacaoService simulacaoService;
    private final EmpresaAtual empresaAtual;

    public CalendarioDaGestaoService(SolicitacaoFeriasRepository solicitacaoRepository,
                                     PeriodoRestritoRepository restritoRepository,
                                     FeriadoRepository feriadoRepository,
                                     ColaboradorRepository colaboradorRepository,
                                     CalendarioService calendarioService,
                                     SimulacaoService simulacaoService,
                                     EmpresaAtual empresaAtual) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.restritoRepository = restritoRepository;
        this.feriadoRepository = feriadoRepository;
        this.colaboradorRepository = colaboradorRepository;
        this.calendarioService = calendarioService;
        this.simulacaoService = simulacaoService;
        this.empresaAtual = empresaAtual;
    }

    @Transactional(readOnly = true)
    public CalendarioDaGestao calendario(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) {
            throw new ParametroInvalidoException("A data final (ate) não pode ser antes da inicial (de).");
        }
        if (ChronoUnit.DAYS.between(de, ate) > CalendarioService.MAXIMO_DE_DIAS) {
            throw new ParametroInvalidoException("O calendário mostra no máximo " + CalendarioService.MAXIMO_DE_DIAS
                    + " dias por vez.");
        }

        Colaborador eu = simulacaoService.colaboradorLogado();
        Long empresaId = empresaAtual.id();
        Long gestorId = eu.getPerfil() == Perfil.ADMIN ? null : eu.getId();

        var ferias = solicitacaoRepository.ausenciasParaOCalendario(
                empresaId, gestorId, StatusSolicitacao.EM_ANDAMENTO_OU_APROVADA, de, ate);

        // Agrupa: departamento → pessoa → as férias dela. LinkedHashMap mantém a ordem de chegada.
        Map<Long, Map<Colaborador, List<CalendarioDaGestao.Ausencia>>> porDepartamento = new LinkedHashMap<>();
        Map<Long, Departamento> departamentos = new LinkedHashMap<>();
        for (var s : ferias) {
            var colaborador = s.getColaborador();
            var departamento = colaborador.getDepartamento();
            Long chave = departamento == null ? null : departamento.getId();
            departamentos.putIfAbsent(chave, departamento);
            porDepartamento.computeIfAbsent(chave, k -> new LinkedHashMap<>())
                    .computeIfAbsent(colaborador, k -> new ArrayList<>())
                    .add(new CalendarioDaGestao.Ausencia(s.getId(), s.getDataInicio(), s.getDataFim(),
                            s.getStatus() != StatusSolicitacao.APROVADA));
        }

        var resposta = porDepartamento.entrySet().stream()
                .map(e -> {
                    var departamento = departamentos.get(e.getKey());
                    var pessoas = e.getValue().entrySet().stream()
                            .map(p -> new CalendarioDaGestao.Pessoa(p.getKey().getId(), p.getKey().getNome(), p.getValue()))
                            .toList();
                    return departamento == null
                            ? new CalendarioDaGestao.Departamento(null, "Sem departamento", pessoas.size(), null, pessoas)
                            : new CalendarioDaGestao.Departamento(departamento.getId(), departamento.getNome(),
                            (int) colaboradorRepository.countByDepartamentoIdAndAtivoTrue(departamento.getId()),
                            calendarioService.limiteDoDepartamento(departamento), pessoas);
                })
                // Em ordem alfabética, com "Sem departamento" por último.
                .sorted(Comparator.comparing((CalendarioDaGestao.Departamento d) -> d.id() == null)
                        .thenComparing(CalendarioDaGestao.Departamento::nome))
                .toList();

        // As restrições que valem para os departamentos da tela, mais as da empresa toda.
        var restricoes = restritoRepository.daEmpresaQueEncostam(empresaId, de, ate).stream()
                .filter(r -> r.getDepartamento() == null || departamentos.containsKey(r.getDepartamento().getId()))
                .map(r -> new CalendarioDaGestao.Restricao(r.getMotivo(),
                        r.getDepartamento() == null ? null : r.getDepartamento().getId(),
                        r.getDataInicio(), r.getDataFim(), r.getTipo() == PeriodoRestrito.Tipo.BLOQUEIO))
                .toList();

        return new CalendarioDaGestao(de, ate, resposta, restricoes,
                feriadoRepository.datasEntre(empresaId, de, ate).stream().sorted().toList());
    }
}
