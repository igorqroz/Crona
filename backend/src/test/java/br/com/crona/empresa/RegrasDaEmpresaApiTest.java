package br.com.crona.empresa;

import br.com.crona.ApiTestBase;
import br.com.crona.compartilhado.RelogioConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cadastros 3: as regras de férias da empresa (GET e PUT /configuracao-ferias), de ponta a ponta.
 * Sem .with(como(...)), a requisição é do ADMIN da empresa de demonstração (ApiTestBase).
 * Cada teste é desfeito no fim (@Transactional), inclusive as regras que ele muda.
 */
class RegrasDaEmpresaApiTest extends ApiTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    private Long rafael;
    private Long ana;
    private Long roberto;

    @BeforeEach
    void ids() {
        rafael = idDe("rafael.costa@demo.crona");
        ana = idDe("ana.lima@demo.crona");
        roberto = idDe("roberto.neves@demo.crona");
    }

    private Long idDe(String email) {
        return jdbc.queryForObject("SELECT id FROM colaborador WHERE email = ?", Long.class, email);
    }

    private String regras(String percentual, String antecedencia, String limite, boolean rh) {
        return """
                {"percentualMinimoTrabalhando": %s, "antecedenciaMinimaDias": %s,
                 "limiteFeriasSimultaneasEmpresa": %s, "exigeConfirmacaoRh": %s}
                """.formatted(percentual, antecedencia, limite, rh);
    }

    private org.springframework.test.web.servlet.ResultActions salvar(String corpo) throws Exception {
        return mockMvc.perform(put("/configuracao-ferias").with(como(rafael, "ADMIN"))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    @Test
    @DisplayName("GET: as regras de hoje, com as pessoas ativas e os pedidos esperando o RH (os números do banco)")
    void buscar() throws Exception {
        long ativas = jdbc.queryForObject(
                "SELECT count(*) FROM colaborador WHERE empresa_id = ? AND ativo", Long.class, empresaDemo);
        long comORh = jdbc.queryForObject("""
                SELECT count(*) FROM solicitacao_ferias s JOIN colaborador c ON c.id = s.colaborador_id
                WHERE c.empresa_id = ? AND s.status = 'AGUARDANDO_RH'
                """, Long.class, empresaDemo);

        mockMvc.perform(get("/configuracao-ferias").with(como(rafael, "ADMIN")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antecedenciaMinimaDias").isNumber())
                .andExpect(jsonPath("$.pessoasAtivas").value(ativas))
                .andExpect(jsonPath("$.pedidosAguardandoRh").value(comORh));
    }

    @Test
    @DisplayName("PUT: grava as 4 regras e o GET seguinte já devolve as novas")
    void salvarEBuscar() throws Exception {
        salvar(regras("60", "45", "4", true))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentualMinimoTrabalhando").value(60))
                .andExpect(jsonPath("$.antecedenciaMinimaDias").value(45))
                .andExpect(jsonPath("$.limiteFeriasSimultaneasEmpresa").value(4))
                .andExpect(jsonPath("$.exigeConfirmacaoRh").value(true));

        mockMvc.perform(get("/configuracao-ferias").with(como(rafael, "ADMIN")))
                .andExpect(jsonPath("$.percentualMinimoTrabalhando").value(60))
                .andExpect(jsonPath("$.antecedenciaMinimaDias").value(45))
                .andExpect(jsonPath("$.limiteFeriasSimultaneasEmpresa").value(4))
                .andExpect(jsonPath("$.exigeConfirmacaoRh").value(true));
    }

    @Test
    @DisplayName("PUT com nulo desliga a EMP-01 e a EMP-04; os limites 0%, 100%, 30 e 365 dias valem")
    void desligarELimites() throws Exception {
        salvar(regras("null", "30", "null", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.percentualMinimoTrabalhando").doesNotExist())
                .andExpect(jsonPath("$.limiteFeriasSimultaneasEmpresa").doesNotExist());
        salvar(regras("0", "365", "1", false)).andExpect(status().isOk());
        salvar(regras("100", "30", "1", false)).andExpect(status().isOk());
    }

    @Test
    @DisplayName("sem configuração gravada: o GET devolve os padrões, e o primeiro PUT cria a linha")
    void semConfiguracao() throws Exception {
        jdbc.update("DELETE FROM configuracao_ferias WHERE empresa_id = ?", empresaDemo);

        mockMvc.perform(get("/configuracao-ferias").with(como(rafael, "ADMIN")))
                .andExpect(jsonPath("$.percentualMinimoTrabalhando").doesNotExist())
                .andExpect(jsonPath("$.antecedenciaMinimaDias").value(30))
                .andExpect(jsonPath("$.limiteFeriasSimultaneasEmpresa").doesNotExist())
                .andExpect(jsonPath("$.exigeConfirmacaoRh").value(false));

        salvar(regras("70", "40", "null", false)).andExpect(status().isOk());

        // Conferido pela API (e não por SQL direto): dentro da transação do teste, o Hibernate só grava
        // no banco quando alguém consulta pela JPA, e o GET faz isso.
        mockMvc.perform(get("/configuracao-ferias").with(como(rafael, "ADMIN")))
                .andExpect(jsonPath("$.percentualMinimoTrabalhando").value(70))
                .andExpect(jsonPath("$.antecedenciaMinimaDias").value(40));
    }

    @Nested
    @DisplayName("valores fora dos limites: 400, com o erro em cada campo")
    class Validacao {

        @Test
        @DisplayName("antecedência menor que 30 (CLT) ou maior que 365")
        void antecedencia() throws Exception {
            salvar(regras("70", "29", "null", false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.antecedenciaMinimaDias")
                            .value("A antecedência mínima não pode ser menor que 30 dias."));
            salvar(regras("70", "366", "null", false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.antecedenciaMinimaDias")
                            .value("A antecedência pode ser de no máximo 365 dias."));
        }

        @Test
        @DisplayName("percentual abaixo de 0 ou acima de 100, e limite da empresa menor que 1")
        void percentualELimite() throws Exception {
            salvar(regras("101", "30", "0", false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.percentualMinimoTrabalhando").value("O percentual vai de 0 a 100."))
                    .andExpect(jsonPath("$.erros.limiteFeriasSimultaneasEmpresa")
                            .value("O limite precisa ser de pelo menos 1 pessoa."));
            salvar(regras("-1", "30", "null", false))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.erros.percentualMinimoTrabalhando").value("O percentual vai de 0 a 100."));
        }

        @Test
        @DisplayName("corpo vazio: 400 (a antecedência faltando não vira 0 em silêncio), nunca 500")
        void corpoVazio() throws Exception {
            salvar("{}").andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("um PUT recusado não muda nada")
        void recusadoNaoMuda() throws Exception {
            salvar(regras("70", "45", "null", false)).andExpect(status().isOk());

            salvar(regras("70", "10", "null", true)).andExpect(status().isBadRequest());

            mockMvc.perform(get("/configuracao-ferias").with(como(rafael, "ADMIN")))
                    .andExpect(jsonPath("$.antecedenciaMinimaDias").value(45))
                    .andExpect(jsonPath("$.exigeConfirmacaoRh").value(false));
        }
    }

    @Nested
    @DisplayName("quem pode (REGRAS_GERENCIAR, só o RH)")
    class QuemPode {

        @Test
        @DisplayName("colaborador e gestor não veem nem mudam as regras (403)")
        void soORh() throws Exception {
            for (var pessoa : new Object[][] {{ana, "COLABORADOR"}, {roberto, "GESTOR"}}) {
                mockMvc.perform(get("/configuracao-ferias").with(como((Long) pessoa[0], (String) pessoa[1])))
                        .andExpect(status().isForbidden());
                mockMvc.perform(put("/configuracao-ferias").with(como((Long) pessoa[0], (String) pessoa[1]))
                                .contentType(MediaType.APPLICATION_JSON).content(regras("0", "30", "null", false)))
                        .andExpect(status().isForbidden());
            }
        }
    }

    /*
     * Decisão do Igor (EMP-06): desligar a confirmação do RH não mexe nos pedidos que já estão
     * AGUARDANDO_RH. Eles continuam na fila do RH até ele confirmar ou recusar.
     */
    @Test
    @DisplayName("EMP-06: desligar a confirmação do RH deixa os pedidos que já esperavam o RH na fila dele")
    void desligarRhNaoMexeNaFila() throws Exception {
        Long periodo = jdbc.queryForObject(
                "SELECT id FROM periodo_aquisitivo WHERE colaborador_id = ? ORDER BY numero DESC LIMIT 1", Long.class, ana);
        LocalDate inicio = LocalDate.now(RelogioConfig.FUSO).plusMonths(4).with(TemporalAdjusters.next(DayOfWeek.MONDAY));
        Long pedido = jdbc.queryForObject("""
                INSERT INTO solicitacao_ferias (colaborador_id, periodo_aquisitivo_id, data_inicio, data_fim, dias_gozo, status)
                VALUES (?, ?, ?, ?, 10, 'AGUARDANDO_RH') RETURNING id
                """, Long.class, ana, periodo, inicio, inicio.plusDays(9));
        salvar(regras("70", "30", "null", true)).andExpect(status().isOk());
        long antes = JsonPath.<Integer>read(mockMvc.perform(get("/configuracao-ferias").with(como(rafael, "ADMIN")))
                .andReturn().getResponse().getContentAsString(), "$.pedidosAguardandoRh");

        salvar(regras("70", "30", "null", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exigeConfirmacaoRh").value(false))
                .andExpect(jsonPath("$.pedidosAguardandoRh").value(antes));

        mockMvc.perform(get("/confirmacoes-rh").with(como(rafael, "ADMIN")))
                .andExpect(jsonPath("$[?(@.id == " + pedido + ")]", hasSize(1)));
    }

    /*
     * O efeito de verdade: a antecedência salva aqui é a que a simulação de férias usa (CLT-07).
     * Uma pessoa nova, admitida há 18 meses (tem saldo em qualquer dia), simula férias a ~50 dias.
     */
    @Test
    @DisplayName("a antecedência salva vale na simulação seguinte: 50 dias passam com 45, e viram atenção com 60")
    void antecedenciaValeNaSimulacao() throws Exception {
        LocalDate hoje = LocalDate.now(RelogioConfig.FUSO);
        var json = mockMvc.perform(post("/colaboradores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "Rui Teste", "email": "rui.teste@qa.crona", "senha": "segredo123",
                         "perfil": "COLABORADOR", "dataAdmissao": "%s"}
                        """.formatted(hoje.minusMonths(18))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long rui = ((Number) JsonPath.read(json, "$.id")).longValue();
        LocalDate inicio = hoje.plusDays(50).with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        long dias = java.time.temporal.ChronoUnit.DAYS.between(hoje, inicio); // de 50 a 56
        String simulacao = """
                {"inicio": "%s", "diasGozo": 10}
                """.formatted(inicio);

        salvar(regras("null", "45", "null", false)).andExpect(status().isOk());
        mockMvc.perform(post("/minhas-ferias/simulacao").with(como(rui, "COLABORADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(simulacao))
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CLT-07')].resultado").value("OK"));

        salvar(regras("null", "60", "null", false)).andExpect(status().isOk());
        mockMvc.perform(post("/minhas-ferias/simulacao").with(como(rui, "COLABORADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content(simulacao))
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CLT-07')].resultado").value("ATENCAO"))
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CLT-07')].mensagem").value(
                        "Faltam só " + dias + " dias para o início; o mínimo é 60 dias de antecedência. O gestor verá isso."));
    }
}
