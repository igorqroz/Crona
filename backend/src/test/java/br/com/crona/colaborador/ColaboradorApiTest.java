package br.com.crona.colaborador;

import br.com.crona.ApiTestBase;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.anyOf;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integração da API de colaboradores, com o banco de verdade.
 * Usa os colaboradores dos dados de desenvolvimento (Roberto, Ana...).
 * Cada teste é desfeito no final (@Transactional).
 */
class ColaboradorApiTest extends ApiTestBase {

    @Autowired
    private ColaboradorRepository colaboradorRepository;
    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    @DisplayName("GET pagina o resultado e informa o total")
    void paginacao() throws Exception {
        mockMvc.perform(get("/colaboradores").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.itens", hasSize(2)))
                .andExpect(jsonPath("$.tamanho").value(2))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(6)));
    }

    @Test
    @DisplayName("GET nunca devolve senha nem hash (armadilha A-01)")
    void semSenhaNaResposta() throws Exception {
        mockMvc.perform(get("/colaboradores").param("busca", "roberto.neves"))
                .andExpect(jsonPath("$.itens[0].nome").value("Roberto Neves"))
                .andExpect(jsonPath("$.itens[0].senha").doesNotExist())
                .andExpect(jsonPath("$.itens[0].senhaHash").doesNotExist());
    }

    @Test
    @DisplayName("GET com ?sort=senhaHash responde 400")
    void ordenacaoProibida() throws Exception {
        mockMvc.perform(get("/colaboradores").param("sort", "senhaHash"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST cadastra com e-mail normalizado, e o hash gravado confere com a senha")
    void cadastrar() throws Exception {
        Long robertoId = idPorBusca("roberto.neves");

        var resposta = mockMvc.perform(post("/colaboradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Paula Nunes", "email": " Paula.Nunes@Demo.Crona ",
                                 "senha": "segredo123", "perfil": "COLABORADOR",
                                 "dataAdmissao": "2025-01-06", "gestorId": %d}
                                """.formatted(robertoId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("paula.nunes@demo.crona"))
                .andExpect(jsonPath("$.gestor.nome").value("Roberto Neves"))
                .andReturn().getResponse().getContentAsString();

        // Confere direto no banco: o que foi gravado é um hash BCrypt da senha enviada.
        Long id = ((Number) JsonPath.read(resposta, "$.id")).longValue();
        var gravado = colaboradorRepository.findById(id).orElseThrow();
        String hash = (String) ReflectionTestUtils.getField(gravado, "senhaHash");
        assertThat(hash).startsWith("$2").isNotEqualTo("segredo123");
        assertThat(passwordEncoder.matches("segredo123", hash)).isTrue();
    }

    @Test
    @DisplayName("POST com e-mail já usado (outras maiúsculas) responde 409")
    void emailRepetido() throws Exception {
        mockMvc.perform(post("/colaboradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Outra Ana", "email": "ANA.LIMA@demo.crona", "senha": "segredo123",
                                 "perfil": "COLABORADOR", "dataAdmissao": "2025-01-06"}
                                """))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST com perfil inexistente responde 400")
    void perfilInvalido() throws Exception {
        mockMvc.perform(post("/colaboradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "X", "email": "x@demo.crona", "senha": "segredo123",
                                 "perfil": "CHEFE", "dataAdmissao": "2025-01-06"}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST com gestor de perfil COLABORADOR responde 422")
    void gestorSemPerfil() throws Exception {
        Long anaId = idPorBusca("ana.lima");

        mockMvc.perform(post("/colaboradores")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "X", "email": "x@demo.crona", "senha": "segredo123",
                                 "perfil": "COLABORADOR", "dataAdmissao": "2025-01-06", "gestorId": %d}
                                """.formatted(anaId)))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.title").value("Regra de negócio"));
    }

    /** Procura um colaborador dos dados de desenvolvimento pela API e devolve o id. */
    private Long idPorBusca(String busca) throws Exception {
        var json = mockMvc.perform(get("/colaboradores").param("busca", busca))
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.itens[0].id")).longValue();
    }

    /** Cadastra uma pessoa de teste pela API (desfeita no fim do teste) e devolve o id. */
    private Long cadastrar(String nome, String perfil, Long gestorId) throws Exception {
        var email = nome.toLowerCase().replace(' ', '.') + "@qa.crona";
        var json = mockMvc.perform(post("/colaboradores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "%s", "email": "%s", "senha": "segredo123", "perfil": "%s",
                         "dataAdmissao": "2024-01-08", "gestorId": %s}
                        """.formatted(nome, email, perfil, gestorId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /*
     * Cadastros 2: o select de "Gestor" do painel pede ?perfil=GESTOR&perfil=ADMIN, e o painel pergunta
     * quantas pessoas alguém tem na equipe com ?gestorId=X (antes de deixar desativar).
     */
    @Nested
    @DisplayName("os filtros de perfil e de gestor (Cadastros 2)")
    class Filtros {

        @Test
        @DisplayName("?perfil=GESTOR&perfil=ADMIN: só gestores e RH (o Roberto e o Rafael, não a Ana)")
        void porPerfil() throws Exception {
            mockMvc.perform(get("/colaboradores").param("perfil", "GESTOR", "ADMIN").param("size", "200"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.itens[*].perfil", everyItem(anyOf(is("GESTOR"), is("ADMIN")))))
                    .andExpect(jsonPath("$.itens[*].nome", hasItems("Roberto Neves", "Rafael Costa")))
                    .andExpect(jsonPath("$.itens[*].nome", not(hasItem("Ana Lima"))));
        }

        @Test
        @DisplayName("?gestorId: só a equipe dessa pessoa; com ?ativo=true, só quem está ativo")
        void porGestor() throws Exception {
            Long gil = cadastrar("Gil Teste", "GESTOR", null);
            cadastrar("Hana Teste", "COLABORADOR", gil);
            Long saiu = cadastrar("Ivo Teste", "COLABORADOR", gil);
            mockMvc.perform(post("/colaboradores/" + saiu + "/desativar")).andExpect(status().isOk());

            mockMvc.perform(get("/colaboradores").param("gestorId", gil.toString()))
                    .andExpect(jsonPath("$.total").value(2))
                    .andExpect(jsonPath("$.itens[*].nome", containsInAnyOrder("Hana Teste", "Ivo Teste")));
            mockMvc.perform(get("/colaboradores").param("gestorId", gil.toString()).param("ativo", "true"))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.itens[0].nome").value("Hana Teste"));
        }

        @Test
        @DisplayName("os dois filtros juntos: os COLABORADORES da equipe (o gestor da equipe não entra)")
        void perfilEGestor() throws Exception {
            Long gil = cadastrar("Gil Teste", "GESTOR", null);
            cadastrar("Hana Teste", "COLABORADOR", gil);
            cadastrar("Jair Teste", "GESTOR", gil);

            mockMvc.perform(get("/colaboradores").param("gestorId", gil.toString()).param("perfil", "COLABORADOR"))
                    .andExpect(jsonPath("$.total").value(1))
                    .andExpect(jsonPath("$.itens[0].nome").value("Hana Teste"));
        }

        @Test
        @DisplayName("perfil que não existe: 400")
        void perfilInvalido() throws Exception {
            mockMvc.perform(get("/colaboradores").param("perfil", "CHEFE"))
                    .andExpect(status().isBadRequest());
        }
    }

    /*
     * BUG-06 (registro em docs/plano-de-testes.md): o RH conseguia desativar o PRÓPRIO acesso. Como o
     * Rafael é o único ADMIN, ninguém mais cuidaria dos cadastros; no site de demonstração, qualquer
     * visitante entra como Rafael. Tirar de si mesmo o perfil de ADMIN tem o mesmo efeito.
     * Regra proposta pelo QA: ninguém desativa o próprio acesso nem muda o próprio perfil (422).
     */
    @Nested
    @DisplayName("BUG-06: o RH não tranca a si mesmo para fora")
    class ProprioAcesso {

        private Long rafael;

        private String meusDadosCom(String perfil) {
            return """
                    {"nome": "Rafael Costa", "email": "rafael.costa@demo.crona", "perfil": "%s",
                     "cargo": "Gestor de RH", "dataAdmissao": "2019-03-11"}
                    """.formatted(perfil);
        }

        @Test
        @DisplayName("o ADMIN não desativa o próprio acesso (422), e continua ativo")
        void naoDesativaASiMesmo() throws Exception {
            rafael = idPorBusca("rafael.costa");

            mockMvc.perform(post("/colaboradores/" + rafael + "/desativar").with(como(rafael, "ADMIN")))
                    .andExpect(status().isUnprocessableContent());

            mockMvc.perform(get("/colaboradores/" + rafael).with(como(rafael, "ADMIN")))
                    .andExpect(jsonPath("$.ativo").value(true));
        }

        @Test
        @DisplayName("o ADMIN não muda o próprio perfil (422), e continua ADMIN")
        void naoMudaOProprioPerfil() throws Exception {
            rafael = idPorBusca("rafael.costa");

            mockMvc.perform(put("/colaboradores/" + rafael).with(como(rafael, "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON).content(meusDadosCom("COLABORADOR")))
                    .andExpect(status().isUnprocessableContent());

            mockMvc.perform(get("/colaboradores/" + rafael).with(como(rafael, "ADMIN")))
                    .andExpect(jsonPath("$.perfil").value("ADMIN"));
        }

        @Test
        @DisplayName("guarda: o ADMIN ainda edita os próprios dados sem mudar o perfil (ex.: o cargo)")
        void editaOsPropriosDados() throws Exception {
            rafael = idPorBusca("rafael.costa");

            mockMvc.perform(put("/colaboradores/" + rafael).with(como(rafael, "ADMIN"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(meusDadosCom("ADMIN").replace("Gestor de RH", "Coordenador de RH")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.cargo").value("Coordenador de RH"));
        }

        @Test
        @DisplayName("guarda: o ADMIN continua desativando OUTRAS pessoas")
        void desativaOutraPessoa() throws Exception {
            rafael = idPorBusca("rafael.costa");
            Long hana = cadastrar("Hana Teste", "COLABORADOR", null);

            mockMvc.perform(post("/colaboradores/" + hana + "/desativar").with(como(rafael, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.ativo").value(false));
        }
    }
}
