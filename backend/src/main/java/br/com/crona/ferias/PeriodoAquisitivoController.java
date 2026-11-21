package br.com.crona.ferias;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * GET /minhas-ferias/periodos-aquisitivos                              os meus (qualquer pessoa logada)
 * GET /colaboradores/{id}/periodos-aquisitivos                         os de um colaborador (gestor e RH)
 * PUT /colaboradores/{id}/periodos-aquisitivos/{numero}/faltas         o RH lança faltas (CLT-02)
 */
@RestController
public class PeriodoAquisitivoController {

    private final PeriodoAquisitivoService service;

    public PeriodoAquisitivoController(PeriodoAquisitivoService service) {
        this.service = service;
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
