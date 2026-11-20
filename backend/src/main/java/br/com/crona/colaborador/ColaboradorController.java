package br.com.crona.colaborador;

import br.com.crona.compartilhado.PaginaResposta;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

/**
 * GET  /colaboradores                    pesquisa paginada
 *      ?busca=ana                        parte do nome ou do e-mail
 *      &departamentoId=1&ativo=true      filtros opcionais
 *      &page=0&size=20&sort=nome,asc     paginação (padrão do Spring Data)
 * GET  /colaboradores/{id}
 * POST /colaboradores                    cadastra (201)
 * PUT  /colaboradores/{id}               altera
 * POST /colaboradores/{id}/desativar     não existe DELETE: colaborador nunca é apagado
 * POST /colaboradores/{id}/reativar
 */
@RestController
@RequestMapping("/colaboradores")
public class ColaboradorController {

    private final ColaboradorService service;

    public ColaboradorController(ColaboradorService service) {
        this.service = service;
    }

    @GetMapping
    public PaginaResposta<ColaboradorResposta> pesquisar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long departamentoId,
            @RequestParam(required = false) Boolean ativo,
            // Sem parâmetros: primeira página, 20 por página, em ordem de nome.
            @PageableDefault(size = 20, sort = "nome", direction = Sort.Direction.ASC) Pageable paginacao) {
        return service.pesquisar(busca, departamentoId, ativo, paginacao);
    }

    @GetMapping("/{id}")
    public ColaboradorResposta buscar(@PathVariable Long id) {
        return service.buscar(id);
    }

    @PostMapping
    public ResponseEntity<ColaboradorResposta> cadastrar(@Valid @RequestBody ColaboradorCadastro dados) {
        var criado = service.cadastrar(dados);
        var localizacao = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(criado.id()).toUri();
        return ResponseEntity.created(localizacao).body(criado);
    }

    @PutMapping("/{id}")
    public ColaboradorResposta atualizar(@PathVariable Long id, @Valid @RequestBody ColaboradorAtualizacao dados) {
        return service.atualizar(id, dados);
    }

    @PostMapping("/{id}/desativar")
    public ColaboradorResposta desativar(@PathVariable Long id) {
        return service.desativar(id);
    }

    @PostMapping("/{id}/reativar")
    public ColaboradorResposta reativar(@PathVariable Long id) {
        return service.reativar(id);
    }
}
