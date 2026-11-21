package br.com.crona.ferias;

import br.com.crona.ApiTestBase;
import br.com.crona.compartilhado.RelogioConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Períodos aquisitivos de ponta a ponta, com a Ana dos dados de desenvolvimento
 * (admitida em 01/02/2024). Os pedidos de férias são inseridos direto no banco,
 * porque a API de solicitação só chega na Fase 5.
 */
class PeriodoAquisitivoApiTest extends ApiTestBase {

    private static final LocalDate ADMISSAO_DA_ANA = LocalDate.of(2024, 2, 1);

    @Autowired
    private JdbcTemplate jdbc;

    private Long anaId;
    private String url;

    @BeforeEach
    void localizarAna() {
        anaId = jdbc.queryForObject("SELECT id FROM colaborador WHERE email = 'ana.lima@demo.crona'", Long.class);
        url = "/colaboradores/" + anaId + "/periodos-aquisitivos";
    }

    @Test
    @DisplayName("lista os períodos com as datas do calendário e 30 dias livres")
    void listar() throws Exception {
        int esperados = CalendarioAquisitivo.periodos(ADMISSAO_DA_ANA, LocalDate.now(RelogioConfig.FUSO)).size();

        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(esperados)))
                .andExpect(jsonPath("$[0].numero").value(1))
                .andExpect(jsonPath("$[0].inicio").value("2024-02-01"))
                .andExpect(jsonPath("$[0].fim").value("2025-01-31"))
                .andExpect(jsonPath("$[0].tirarAte").value("2026-01-31"))
                .andExpect(jsonPath("$[0].direito").value(30))
                .andExpect(jsonPath("$[0].livres").value(30));
    }

    @Test
    @DisplayName("um pedido de 15 dias esperando o gestor: 15 em pedido e 15 livres (a tela da Ana)")
    void pedidoEmAndamento() throws Exception {
        inserirPedido(2, 15, 0, "AGUARDANDO_GESTOR");

        mockMvc.perform(get(url))
                .andExpect(jsonPath("$[1].emPedido").value(15))
                .andExpect(jsonPath("$[1].livres").value(15));
    }

    @Test
    @DisplayName("abono conta no saldo: 20 de descanso + 10 vendidos esgotam o período")
    void abonoContaNoSaldo() throws Exception {
        inserirPedido(2, 20, 10, "APROVADA");

        mockMvc.perform(get(url))
                .andExpect(jsonPath("$[1].aprovados").value(30))
                .andExpect(jsonPath("$[1].livres").value(0))
                .andExpect(jsonPath("$[1].situacao").value("ESGOTADO"));
    }

    @Test
    @DisplayName("RH lança 7 faltas: o direito cai para 24 dias (CLT-02)")
    void lancarFaltas() throws Exception {
        mockMvc.perform(put(url + "/2/faltas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"faltas": 7}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.faltasInjustificadas").value(7))
                .andExpect(jsonPath("$.direito").value(24));
    }

    @Test
    @DisplayName("faltas que deixariam o direito abaixo do já aprovado: 422")
    void faltasAbaixoDoUsado() throws Exception {
        inserirPedido(2, 20, 0, "APROVADA");

        mockMvc.perform(put(url + "/2/faltas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"faltas": 15}
                        """))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("faltas negativas: 400")
    void faltasNegativas() throws Exception {
        mockMvc.perform(put(url + "/2/faltas").contentType(MediaType.APPLICATION_JSON).content("""
                        {"faltas": -1}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a Ana vê os próprios períodos em /minhas-ferias, mas não pela rota do RH")
    void minhasFerias() throws Exception {
        mockMvc.perform(get("/minhas-ferias/periodos-aquisitivos").with(como(anaId, "COLABORADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].inicio").value("2024-02-01"));

        mockMvc.perform(get(url).with(como(anaId, "COLABORADOR")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("cadastrar um colaborador já gera os períodos dele")
    void cadastroGeraPeriodos() throws Exception {
        var admissao = LocalDate.now(RelogioConfig.FUSO).minusYears(2).minusMonths(1);

        var json = mockMvc.perform(post("/colaboradores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "Nova Pessoa", "email": "nova.pessoa@demo.crona", "senha": "segredo123",
                         "perfil": "COLABORADOR", "dataAdmissao": "%s"}
                        """.formatted(admissao)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long novoId = ((Number) com.jayway.jsonpath.JsonPath.read(json, "$.id")).longValue();

        // 2 anos e 1 mês de casa: 2 períodos completos + 1 em andamento.
        mockMvc.perform(get("/colaboradores/" + novoId + "/periodos-aquisitivos"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[2].situacao").value("EM_AQUISICAO"));
    }

    @Test
    @DisplayName("mudar a admissão para depois de um período que já tem pedido: 422")
    void admissaoNaoPodePularPeriodoComPedido() throws Exception {
        inserirPedido(2, 15, 0, "AGUARDANDO_GESTOR");
        var ana = jdbc.queryForMap("SELECT departamento_id, gestor_id FROM colaborador WHERE id = ?", anaId);

        mockMvc.perform(put("/colaboradores/" + anaId).contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "Ana Lima", "email": "ana.lima@demo.crona", "perfil": "COLABORADOR",
                         "dataAdmissao": "%s", "departamentoId": %s, "gestorId": %s}
                        """.formatted(LocalDate.now(RelogioConfig.FUSO), ana.get("departamento_id"), ana.get("gestor_id"))))
                .andExpect(status().isUnprocessableContent());
    }

    /** Insere um pedido de férias da Ana direto no banco, no período de número indicado. */
    private void inserirPedido(int numeroDoPeriodo, int diasGozo, int diasAbono, String status) {
        Long periodoId = jdbc.queryForObject(
                "SELECT id FROM periodo_aquisitivo WHERE colaborador_id = ? AND numero = ?",
                Long.class, anaId, numeroDoPeriodo);
        jdbc.update("""
                INSERT INTO solicitacao_ferias
                    (colaborador_id, periodo_aquisitivo_id, data_inicio, data_fim, dias_gozo, dias_abono, status)
                VALUES (?, ?, DATE '2027-03-17', DATE '2027-03-17' + ? - 1, ?, ?, ?)
                """, anaId, periodoId, diasGozo, diasGozo, diasAbono, status);
    }
}
