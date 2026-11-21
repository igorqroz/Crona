package br.com.crona.ferias;

import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.compartilhado.ReferenciaResposta;
import br.com.crona.compartilhado.erro.ParametroInvalidoException;
import br.com.crona.empresa.ConfiguracaoFerias;
import br.com.crona.empresa.ConfiguracaoFeriasRepository;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.ferias.validacao.DadosDaEquipe;
import br.com.crona.ferias.validacao.ValidadorDaEquipe;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;

/**
 * Os dados do calendário da tela "Solicitar férias" (Fase 7b): quem do meu departamento está
 * fora em cada dia, o limite da EMP-01 e os feriados. A tela desenha as "barrinhas" com isso
 * ANTES de o colaborador escolher as datas; a conferência de verdade continua na simulação.
 */
@Service
public class CalendarioService {

    /** Uns 4 meses: o suficiente para a tela (2 meses por vez) sem deixar alguém pedir anos de dados. */
    static final int MAXIMO_DE_DIAS = 124;

    private final SimulacaoService simulacaoService;
    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final ColaboradorRepository colaboradorRepository;
    private final ConfiguracaoFeriasRepository configuracaoRepository;
    private final FeriadoRepository feriadoRepository;
    private final EmpresaAtual empresaAtual;

    public CalendarioService(SimulacaoService simulacaoService,
                             SolicitacaoFeriasRepository solicitacaoRepository,
                             ColaboradorRepository colaboradorRepository,
                             ConfiguracaoFeriasRepository configuracaoRepository,
                             FeriadoRepository feriadoRepository,
                             EmpresaAtual empresaAtual) {
        this.simulacaoService = simulacaoService;
        this.solicitacaoRepository = solicitacaoRepository;
        this.colaboradorRepository = colaboradorRepository;
        this.configuracaoRepository = configuracaoRepository;
        this.feriadoRepository = feriadoRepository;
        this.empresaAtual = empresaAtual;
    }

    @Transactional(readOnly = true)
    public CalendarioDaEquipe calendario(LocalDate de, LocalDate ate) {
        if (ate.isBefore(de)) {
            throw new ParametroInvalidoException("A data final (ate) não pode ser antes da inicial (de).");
        }
        if (ChronoUnit.DAYS.between(de, ate) > MAXIMO_DE_DIAS) {
            throw new ParametroInvalidoException("O calendário mostra no máximo " + MAXIMO_DE_DIAS + " dias por vez.");
        }

        var eu = simulacaoService.colaboradorLogado();
        Long empresaId = empresaAtual.id();
        var departamento = eu.getDepartamento();
        var gestor = eu.getGestor() == null ? null
                : new ReferenciaResposta(eu.getGestor().getId(), eu.getGestor().getNome());
        var feriados = feriadoRepository.datasEntre(empresaId, de, ate).stream().sorted().toList();

        if (departamento == null) {
            return new CalendarioDaEquipe(null, null, gestor, List.of(), feriados);
        }

        // A mesma consulta da simulação, só que só com os colegas do MEU departamento.
        List<CalendarioDaEquipe.Ausencia> ausencias = solicitacaoRepository.ausenciasDosColegas(
                        empresaId, eu.getId(), StatusSolicitacao.EM_ANDAMENTO_OU_APROVADA, de, ate).stream()
                .filter(a -> Objects.equals(a.departamentoId(), departamento.getId()))
                .map(a -> new CalendarioDaEquipe.Ausencia(a.nome(), a.inicio(), a.fim(),
                        a.status() != StatusSolicitacao.APROVADA))
                .toList();

        var config = configuracaoRepository.findByEmpresaId(empresaId).orElseGet(ConfiguracaoFerias::padrao);
        Integer limite = config.getPercentualMinimoTrabalhando() == null ? null
                : ValidadorDaEquipe.limiteDoDepartamento(new DadosDaEquipe.Departamento(departamento.getNome(),
                (int) colaboradorRepository.countByDepartamentoIdAndAtivoTrue(departamento.getId()),
                config.getPercentualMinimoTrabalhando()));

        return new CalendarioDaEquipe(departamento.getNome(), limite, gestor, ausencias, feriados);
    }
}
