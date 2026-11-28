package br.com.crona.empresa;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastros 3: as regras de férias da empresa (só o RH).
 *
 * GET /configuracao-ferias   as regras de hoje (ou os padrões, se nunca foram salvas)
 * PUT /configuracao-ferias   muda as regras
 */
@RestController
@RequestMapping("/configuracao-ferias")
public class RegrasDaEmpresaController {

    private final RegrasDaEmpresaService service;

    public RegrasDaEmpresaController(RegrasDaEmpresaService service) {
        this.service = service;
    }

    @PreAuthorize("hasAuthority('REGRAS_GERENCIAR')")
    @GetMapping
    public RegrasDaEmpresaResposta buscar() {
        return service.buscar();
    }

    @PreAuthorize("hasAuthority('REGRAS_GERENCIAR')")
    @PutMapping
    public RegrasDaEmpresaResposta alterar(@Valid @RequestBody RegrasDaEmpresa regras) {
        return service.alterar(regras);
    }
}
