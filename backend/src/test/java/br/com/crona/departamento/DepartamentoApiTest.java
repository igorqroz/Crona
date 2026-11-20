package br.com.crona.departamento;

import br.com.crona.ApiTestBase;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * TESTE DE INTEGRAÇÃO: sobe a aplicação inteira (controller, service, repository,
 * Flyway, banco de verdade) e faz requisições HTTP simuladas com o MockMvc.
 *
 * Testa o que o teste unitário não pega: o JSON, os códigos HTTP, a validação,
 * o tratamento de erros e o SQL que o Spring Data gera.
 *
 * {@code @Transactional}: cada teste roda dentro de uma transação que é DESFEITA
 * no final (ROLLBACK). Assim os testes não deixam lixo no banco (armadilha A-08)
 * e um teste não interfere no outro.
 */
class DepartamentoApiTest extends ApiTestBase {

    @Test
    @DisplayName("GET /departamentos lista os departamentos da empresa em ordem alfabética")
    void listar() throws Exception {
        mockMvc.perform(get("/departamentos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", hasItem("Engenharia")))
                .andExpect(jsonPath("$[0].nome").value("Comercial"));
    }

    @Test
    @DisplayName("POST cria, responde 201 com Location, e o GET encontra")
    void criarEBuscar() throws Exception {
        var resposta = mockMvc.perform(post("/departamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "  Jurídico  "}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().exists("Location"))
                .andExpect(jsonPath("$.nome").value("Jurídico"))
                .andReturn();

        String location = resposta.getResponse().getHeader("Location");
        mockMvc.perform(get(location))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Jurídico"));
    }

    @Test
    @DisplayName("POST com nome repetido (sem diferenciar maiúsculas) responde 409")
    void criarRepetido() throws Exception {
        mockMvc.perform(post("/departamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "engenharia"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Conflito"));
    }

    @Test
    @DisplayName("POST sem nome responde 400 com o erro no campo")
    void criarSemNome() throws Exception {
        mockMvc.perform(post("/departamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": ""}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.erros.nome").value("Informe o nome do departamento."));
    }

    @Test
    @DisplayName("PUT renomeia; DELETE exclui e o departamento some da lista")
    void renomearEExcluir() throws Exception {
        var criado = mockMvc.perform(post("/departamentos")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Temporário"}
                                """))
                .andReturn().getResponse().getHeader("Location");

        mockMvc.perform(put(criado)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Temporário 2"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Temporário 2"));

        mockMvc.perform(delete(criado))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/departamentos"))
                .andExpect(jsonPath("$[*].nome", not(hasItem("Temporário 2"))));
    }

    @Test
    @DisplayName("GET de um id que não existe responde 404")
    void buscarInexistente() throws Exception {
        mockMvc.perform(get("/departamentos/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Departamento 999999 não encontrado."));
    }
}
