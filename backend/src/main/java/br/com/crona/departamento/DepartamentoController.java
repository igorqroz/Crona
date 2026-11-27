package br.com.crona.departamento;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.util.List;

/**
 * Endpoints REST de departamentos. Só traduz HTTP <-> service; nenhuma regra mora aqui.
 *
 * GET    /departamentos        lista os departamentos da empresa
 * GET    /departamentos/{id}   busca um
 * POST   /departamentos        cria       (201 Created)
 * PUT    /departamentos/{id}   renomeia   (200 OK)
 * DELETE /departamentos/{id}   exclui     (204 No Content)
 */
@RestController
@RequestMapping("/departamentos")
public class DepartamentoController {

    private final DepartamentoService service;
    private final ResumoDosDepartamentos resumo;

    public DepartamentoController(DepartamentoService service, ResumoDosDepartamentos resumo) {
        this.service = service;
        this.resumo = resumo;
    }

    // A lista já vem com quantas pessoas ativas cada departamento tem e o limite da EMP-01 (tela de Cadastros).
    @PreAuthorize("hasAuthority('DEPARTAMENTO_VER')")
    @GetMapping
    public List<DepartamentoResumo> listar() {
        return resumo.listar();
    }

    @PreAuthorize("hasAuthority('DEPARTAMENTO_VER')")
    @GetMapping("/{id}")
    public DepartamentoResposta buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    // @Valid: confere as anotações do record (@NotBlank, @Size) antes de chamar o service.
    @PreAuthorize("hasAuthority('DEPARTAMENTO_GERENCIAR')")
    @PostMapping
    public ResponseEntity<DepartamentoResposta> criar(@Valid @RequestBody DepartamentoRequisicao requisicao) {
        var criado = service.criar(requisicao);
        // Padrão REST: responder 201 com o cabeçalho Location apontando para o novo recurso.
        var localizacao = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(localizacao).body(criado);
    }

    @PreAuthorize("hasAuthority('DEPARTAMENTO_GERENCIAR')")
    @PutMapping("/{id}")
    public DepartamentoResposta renomear(@PathVariable Long id, @Valid @RequestBody DepartamentoRequisicao requisicao) {
        return service.renomear(id, requisicao);
    }

    @PreAuthorize("hasAuthority('DEPARTAMENTO_GERENCIAR')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> excluir(@PathVariable Long id) {
        service.excluir(id);
        return ResponseEntity.noContent().build();
    }
}
