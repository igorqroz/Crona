package br.com.crona.ferias;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * GET /gestao/calendario?de=&ate=   quem está fora (Fase 8c): a equipe do gestor ou a empresa toda (RH)
 *
 * COLABORADOR_VER: a mesma permissão de ver os colaboradores (gestor e ADMIN têm).
 */
@RestController
public class CalendarioDaGestaoController {

    private final CalendarioDaGestaoService service;

    public CalendarioDaGestaoController(CalendarioDaGestaoService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('COLABORADOR_VER')")
    @GetMapping("/gestao/calendario")
    public CalendarioDaGestao calendario(@RequestParam LocalDate de, @RequestParam LocalDate ate) {
        return service.calendario(de, ate);
    }
}
