package br.com.crona;

import br.com.crona.empresa.EmpresaRepository;
import br.com.crona.seguranca.ConversorDePermissoes;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Base dos testes de integração da API que precisam de um usuário LOGADO.
 *
 * Depois da Fase 3, toda rota exige token. Em vez de fazer login em cada teste, o
 * spring-security-test "injeta" um token já aceito em todas as requisições (jwt()).
 * Os testes do login em si, que precisam de token de verdade, ficam no SegurancaApiTest.
 *
 * O usuário simulado é um ADMIN da Empresa Demo (dados de desenvolvimento).
 */
@SpringBootTest
@Transactional
public abstract class ApiTestBase {

    public static final String CNPJ_EMPRESA_DEMO = "11222333000181";

    protected MockMvc mockMvc;

    @Autowired
    private EmpresaRepository empresaRepository;

    protected Long empresaDemo;

    /**
     * Para um teste agir como OUTRA pessoa, e não como o ADMIN padrão:
     *     mockMvc.perform(get("/minhas-ferias/...").with(como(anaId, "COLABORADOR")))
     */
    protected RequestPostProcessor como(Long colaboradorId, String perfil) {
        return jwt()
                .jwt(token -> token
                        .subject(colaboradorId.toString())
                        .claim("empresa_id", empresaDemo)
                        .claim("perfil", perfil))
                .authorities(new ConversorDePermissoes());
    }

    @BeforeEach
    void montarMockMvcComUsuarioLogado(WebApplicationContext contexto) {
        empresaDemo = empresaRepository.findByCnpj(CNPJ_EMPRESA_DEMO).orElseThrow().getId();

        mockMvc = MockMvcBuilders.webAppContextSetup(contexto)
                .apply(springSecurity())                        // liga os filtros do Spring Security
                .defaultRequest(get("/").with(jwt()                // todo request leva este token
                        .jwt(token -> token
                                .subject("1")
                                .claim("empresa_id", empresaDemo)
                                .claim("perfil", "ADMIN"))
                        // Calcula as permissões do perfil do mesmo jeito que a API faz de verdade.
                        .authorities(new ConversorDePermissoes())))
                .defaultResponseCharacterEncoding(StandardCharsets.UTF_8)
                .build();
    }
}
