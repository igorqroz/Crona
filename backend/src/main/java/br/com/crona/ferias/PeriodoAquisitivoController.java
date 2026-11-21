package br.com.crona.ferias;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * GET  /minhas-ferias/periodos-aquisitivos                             os meus (qualquer pessoa logada)
 * POST /minhas-ferias/simulacao                                       confere um pedido sem gravar
 * GET  /minhas-ferias/calendario?de=&ate=                             quem do meu departamento está fora, limite e feriados
 * GET /colaboradores/{id}/periodos-aquisitivos                         os de um colaborador (gestor e RH)
 * PUT /colaboradores/{id}/periodos-aquisitivos/{numero}/faltas         o RH lança faltas (CLT-02)
 */
@RestController
public class PeriodoAquisitivoController {

    private final PeriodoAquisitivoService service;
    private final SimulacaoService simulacaoService;
    private final CalendarioService calendarioService;

    public PeriodoAquisitivoController(PeriodoAquisitivoService service, SimulacaoService simulacaoService,
                                       CalendarioService calendarioService) {
        this.service = service;
        this.simulacaoService = simulacaoService;
        this.calendarioService = calendarioService;
    }

    /**
     * Confere um pedido SEM gravar nada: devolve o checklist para a tela do calendário.
     * POST (e não GET) só porque recebe um corpo JSON; não altera nada no servidor.
     */
    @PostMapping("/minhas-ferias/simulacao")
    public SimulacaoResposta simular(@Valid @RequestBody SimulacaoRequisicao requisicao) {
        return simulacaoService.simular(requisicao);
    }

    /**
     * Fase 7b: as "barrinhas" do calendário. Ex.: /minhas-ferias/calendario?de=2026-12-01&ate=2027-01-31
     * Sem @PreAuthorize: basta estar logado, e só mostra o departamento de quem pediu.
     */
    @GetMapping("/minhas-ferias/calendario")
    public CalendarioDaEquipe calendario(@RequestParam LocalDate de, @RequestParam LocalDate ate) {
        return calendarioService.calendario(de, ate);
    }

    // Sem @PreAuthorize: basta estar logado, e o service só devolve os períodos de quem pediu.
    @GetMapping("/minhas-ferias/periodos-aquisitivos")
    public List<PeriodoAquisitivoResposta> meus() {
        return service.meus();
    }

    @PreAuthorize("hasAuthority('COLABORADOR_VER')")
    @GetMapping("/colaboradores/{colaboradorId}/periodos-aquisitivos")
    public List<PeriodoAquisitivoResposta> doColaborador(@PathVariable Long colaboradorId) {
        return service.doColaborador(colaboradorId);
    }

    @PreAuthorize("hasAuthority('COLABORADOR_GERENCIAR')")
    @PutMapping("/colaboradores/{colaboradorId}/periodos-aquisitivos/{numero}/faltas")
    public PeriodoAquisitivoResposta registrarFaltas(@PathVariable Long colaboradorId, @PathVariable int numero,
                                                     @Valid @RequestBody FaltasRequisicao requisicao) {
        return service.registrarFaltas(colaboradorId, numero, requisicao.faltas());
    }
}
