package br.com.crona.colaborador;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Teste de integração da API de colaboradores, com o banco de verdade.
 * Usa os colaboradores dos dados de desenvolvimento (Roberto, Ana...).
 * Cada teste é desfeito no final (@Transactional).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ColaboradorApiTest {

    @Autowired
    private MockMvc mockMvc;
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
}
