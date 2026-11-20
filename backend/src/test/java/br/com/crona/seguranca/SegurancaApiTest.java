package br.com.crona.seguranca;

import br.com.crona.ApiTestBase;
import br.com.crona.colaborador.ColaboradorRepository;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.contains;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Testes de segurança com TOKENS DE VERDADE: login de verdade, token assinado de verdade.
 * (Os outros testes de API simulam o token; este testa justamente o que eles pulam.)
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SegurancaApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtEncoder jwtEncoder;
    @Autowired
    private JwtPropriedades propriedades;
    @Autowired
    private ColaboradorRepository colaboradorRepository;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    @DisplayName("sem token: 401 'Faça login para continuar.'")
    void semToken() throws Exception {
        mockMvc.perform(get("/departamentos"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Faça login para continuar."));
    }

    @Test
    @DisplayName("login com senha certa devolve um token que abre as outras rotas")
    void loginEUsoDoToken() throws Exception {
        String token = entrar("rafael.costa@demo.crona", "crona123");

        mockMvc.perform(get("/departamentos").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("senha errada e e-mail inexistente recebem exatamente a mesma resposta")
    void loginErrado() throws Exception {
        for (String email : new String[]{"rafael.costa@demo.crona", "ninguem@demo.crona"}) {
            mockMvc.perform(post("/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"email": "%s", "senha": "senha-errada"}
                                    """.formatted(email)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.detail").value("E-mail ou senha inválidos."));
        }
    }

    @Test
    @DisplayName("token adulterado (um caractere trocado) é recusado")
    void tokenAdulterado() throws Exception {
        String token = entrar("ana.lima@demo.crona", "crona123");
        // Troca um caractere no meio dos dados: a assinatura deixa de bater.
        int meio = token.indexOf('.') + 10;
        char trocado = token.charAt(meio) == 'A' ? 'B' : 'A';
        String adulterado = token.substring(0, meio) + trocado + token.substring(meio + 1);

        mockMvc.perform(get("/departamentos").header(HttpHeaders.AUTHORIZATION, "Bearer " + adulterado))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.detail").value("Token inválido ou expirado. Faça login de novo."));
    }

    @Test
    @DisplayName("token vencido é recusado")
    void tokenVencido() throws Exception {
        var ana = colaboradorRepository.findByEmailIgnoreCase("ana.lima@demo.crona").orElseThrow();
        // Um relógio parado 9 horas atrás: o token "nasce" já vencido (validade de 8h).
        var relogioNoPassado = Clock.fixed(Instant.now().minus(Duration.ofHours(9)), ZoneOffset.UTC);
        String vencido = new TokenService(jwtEncoder, propriedades, relogioNoPassado).gerar(ana).getTokenValue();

        mockMvc.perform(get("/departamentos").header(HttpHeaders.AUTHORIZATION, "Bearer " + vencido))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("isolamento entre empresas: quem é da empresa B não vê nem acessa dados da empresa A")
    void isolamentoEntreEmpresas() throws Exception {
        // Cria uma segunda empresa com um departamento (some no fim do teste, por causa do @Transactional).
        Long empresaB = jdbc.queryForObject(
                "INSERT INTO empresa (razao_social, cnpj) VALUES ('Empresa B', '99888777000166') RETURNING id", Long.class);
        jdbc.update("INSERT INTO departamento (empresa_id, nome) VALUES (?, 'Só da B')", empresaB);
        Long engenhariaDaDemo = jdbc.queryForObject(
                "SELECT d.id FROM departamento d JOIN empresa e ON e.id = d.empresa_id "
                        + "WHERE e.cnpj = ? AND d.nome = 'Engenharia'", Long.class, ApiTestBase.CNPJ_EMPRESA_DEMO);

        var usuarioDaB = jwt().jwt(t -> t.subject("999").claim("empresa_id", empresaB).claim("perfil", "ADMIN"))
                .authorities(new ConversorDePermissoes());

        // A lista só traz o departamento da B...
        mockMvc.perform(get("/departamentos").with(usuarioDaB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].nome", contains("Só da B")));
        // ...e um id da empresa Demo, mesmo existindo, responde 404 para a B.
        mockMvc.perform(get("/departamentos/" + engenhariaDaDemo).with(usuarioDaB))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ permissões (403)

    @Test
    @DisplayName("COLABORADOR (Ana): vê departamentos, mas não lista nem cadastra colaboradores")
    void permissoesDoColaborador() throws Exception {
        String ana = "Bearer " + entrar("ana.lima@demo.crona", "crona123");

        mockMvc.perform(get("/departamentos").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isOk());
        mockMvc.perform(get("/colaboradores").header(HttpHeaders.AUTHORIZATION, ana))
                .andExpect(status().isForbidden());
        // O buraco encontrado no teste manual: a Ana conseguia criar um ADMIN.
        mockMvc.perform(post("/colaboradores").header(HttpHeaders.AUTHORIZATION, ana)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOVO_ADMIN))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Você não tem permissão para esta ação."));
    }

    @Test
    @DisplayName("GESTOR (Roberto): consulta colaboradores, mas não cadastra nem mexe em departamentos")
    void permissoesDoGestor() throws Exception {
        String roberto = "Bearer " + entrar("roberto.neves@demo.crona", "crona123");

        mockMvc.perform(get("/colaboradores").header(HttpHeaders.AUTHORIZATION, roberto))
                .andExpect(status().isOk());
        mockMvc.perform(post("/colaboradores").header(HttpHeaders.AUTHORIZATION, roberto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOVO_ADMIN))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/departamentos").header(HttpHeaders.AUTHORIZATION, roberto)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome": "Departamento do Roberto"}
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("ADMIN (Rafael): cadastra colaborador")
    void permissoesDoAdmin() throws Exception {
        String rafael = "Bearer " + entrar("rafael.costa@demo.crona", "crona123");

        mockMvc.perform(post("/colaboradores").header(HttpHeaders.AUTHORIZATION, rafael)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(NOVO_ADMIN))
                .andExpect(status().isCreated());
    }

    private static final String NOVO_ADMIN = """
            {"nome": "Teste", "email": "teste.permissao@demo.crona", "senha": "segredo123",
             "perfil": "ADMIN", "dataAdmissao": "2025-01-06"}
            """;

    private String entrar(String email, String senha) throws Exception {
        var json = mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email": "%s", "senha": "%s"}
                                """.formatted(email, senha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipo").value("Bearer"))
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(json, "$.token");
    }
}
