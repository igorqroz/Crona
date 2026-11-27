package br.com.crona.departamento;

import br.com.crona.ApiTestBase;

import com.jayway.jsonpath.JsonPath;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
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

    /*
     * Cadastros 1: a lista traz o resumo de cada departamento ("5 pessoas · até 1 fora por dia").
     * As pessoas são só as ATIVAS, e o limite é a mesma conta da EMP-01 que o pedido de férias usa:
     * pessoas × (100 − percentual mínimo trabalhando) / 100, no mínimo 1.
     */
    @Nested
    @DisplayName("o resumo da lista (pessoas e limite da EMP-01)")
    class Resumo {

        @Autowired
        private JdbcTemplate jdbc;
        @Autowired
        private EntityManager sessao;

        private Long criarDepartamento(String nome) throws Exception {
            var json = mockMvc.perform(post("/departamentos").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"nome\": \"" + nome + "\"}"))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return ((Number) JsonPath.read(json, "$.id")).longValue();
        }

        private Long criarPessoa(Long departamentoId, int n) throws Exception {
            var json = mockMvc.perform(post("/colaboradores").contentType(MediaType.APPLICATION_JSON).content("""
                            {"nome": "Pessoa %d", "email": "pessoa%d.juridico@demo.crona", "senha": "segredo123",
                             "perfil": "COLABORADOR", "dataAdmissao": "2024-01-08", "departamentoId": %d}
                            """.formatted(n, n, departamentoId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            return ((Number) JsonPath.read(json, "$.id")).longValue();
        }

        private void percentualMinimoTrabalhando(Integer percentual) {
            jdbc.update("UPDATE configuracao_ferias SET percentual_minimo_trabalhando = ? WHERE empresa_id = ?",
                    percentual, empresaDemo);
        }

        private String linha(Long id) {
            return "$[?(@.id == " + id + ")]";
        }

        @Test
        @DisplayName("conta só as pessoas ATIVAS, e o limite usa a conta da EMP-01")
        void pessoasAtivasELimite() throws Exception {
            percentualMinimoTrabalhando(50);
            Long juridico = criarDepartamento("Jurídico");
            for (int n = 1; n <= 4; n++) {
                criarPessoa(juridico, n);
            }
            Long saiu = criarPessoa(juridico, 5);
            mockMvc.perform(post("/colaboradores/" + saiu + "/desativar")).andExpect(status().is2xxSuccessful());

            // 4 ativas × 50% = 2 podem estar fora no mesmo dia.
            mockMvc.perform(get("/departamentos"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath(linha(juridico) + ".pessoas", contains(4)))
                    .andExpect(jsonPath(linha(juridico) + ".limiteFora", contains(2)));
        }

        @Test
        @DisplayName("departamento sem ninguém: 0 pessoas e limite 1 (o mínimo da EMP-01)")
        void departamentoVazio() throws Exception {
            percentualMinimoTrabalhando(50);
            Long vazio = criarDepartamento("Jurídico");

            mockMvc.perform(get("/departamentos"))
                    .andExpect(jsonPath(linha(vazio) + ".pessoas", contains(0)))
                    .andExpect(jsonPath(linha(vazio) + ".limiteFora", contains(1)));
        }

        /*
         * O banco não deixa excluir um departamento que alguém ainda referencia, mesmo uma pessoa
         * INATIVA (que o resumo não conta). A API traduz isso em 409.
         *
         * flush() + clear(): no teste, todas as requisições dividem a mesma sessão do Hibernate, que
         * ainda "lembra" a pessoa ligada ao departamento e falha antes de chegar ao banco (500).
         * Limpar a sessão simula o que acontece de verdade: cada requisição com a sua.
         */
        @Test
        @DisplayName("departamento só com gente inativa: o resumo diz 0, e excluir responde 409 (está em uso)")
        void excluirComGenteInativa() throws Exception {
            Long juridico = criarDepartamento("Jurídico");
            Long saiu = criarPessoa(juridico, 1);
            mockMvc.perform(post("/colaboradores/" + saiu + "/desativar")).andExpect(status().is2xxSuccessful());
            mockMvc.perform(get("/departamentos"))
                    .andExpect(jsonPath(linha(juridico) + ".pessoas", contains(0)));
            sessao.flush();
            sessao.clear();

            mockMvc.perform(delete("/departamentos/" + juridico))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value("O departamento \"Jurídico\" está em uso e não pode ser excluído."));
        }

        @Test
        @DisplayName("com a EMP-01 desligada (sem percentual), o limite vem nulo")
        void semEmp01() throws Exception {
            percentualMinimoTrabalhando(null);
            Long juridico = criarDepartamento("Jurídico");
            criarPessoa(juridico, 1);

            mockMvc.perform(get("/departamentos"))
                    .andExpect(jsonPath(linha(juridico) + ".pessoas", contains(1)))
                    .andExpect(jsonPath(linha(juridico) + ".limiteFora", contains(nullValue())));
        }

        @Test
        @DisplayName("o COLABORADOR também vê a lista (DEPARTAMENTO_VER), mas não cria (403)")
        void colaboradorSoVe() throws Exception {
            Long ana = jdbc.queryForObject("SELECT id FROM colaborador WHERE email = 'ana.lima@demo.crona'", Long.class);

            mockMvc.perform(get("/departamentos").with(como(ana, "COLABORADOR")))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/departamentos").with(como(ana, "COLABORADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"nome\": \"Jurídico\"}"))
                    .andExpect(status().isForbidden());
        }
    }
}
