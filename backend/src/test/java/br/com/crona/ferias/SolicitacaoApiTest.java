package br.com.crona.ferias;

import br.com.crona.compartilhado.RelogioConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** O lado do colaborador: enviar, acompanhar e cancelar pedidos. */
class SolicitacaoApiTest extends ComBiaTestBase {

    private ResultActions enviar(LocalDate inicio, int diasGozo, String recado) throws Exception {
        return mockMvc.perform(post("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"inicio": "%s", "diasGozo": %d, "recado": %s}
                        """.formatted(inicio, diasGozo, recado == null ? "null" : "\"" + recado + "\"")));
    }

    private Long idDe(ResultActions resposta) throws Exception {
        return ((Number) JsonPath.read(resposta.andReturn().getResponse().getContentAsString(), "$.id")).longValue();
    }

    @Test
    @DisplayName("enviar um pedido válido: 201, esperando o gestor, e os dias já saem do saldo")
    void enviarValido() throws Exception {
        enviar(segunda, 15, "Viagem já comprada")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("AGUARDANDO_GESTOR"))
                .andExpect(jsonPath("$.fim").value(segunda.plusDays(14).toString()))
                .andExpect(jsonPath("$.recado").value("Viagem já comprada"));

        mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/minhas-ferias/periodos-aquisitivos").with(como(biaId, "COLABORADOR")))
                .andExpect(jsonPath("$[0].emPedido").value(15))
                .andExpect(jsonPath("$[0].livres").value(15));
    }

    @Test
    @DisplayName("pedido com impedimento: 422 com o checklist, e nada é gravado")
    void enviarComImpedimento() throws Exception {
        enviar(segunda.plusDays(3), 15, null)       // quinta-feira
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CLT-05')].resultado").value("IMPEDIMENTO"));

        mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("o pedido pendente reserva os dias: um segundo pedido que passa do saldo é recusado")
    void pendenteReservaSaldo() throws Exception {
        enviar(segunda, 20, null).andExpect(status().isCreated());

        enviar(segunda.plusWeeks(5), 15, null)
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'SALDO')].resultado").value("IMPEDIMENTO"));
    }

    @Test
    @DisplayName("CRONA-01: as mesmas férias pedidas de novo são recusadas, mesmo cabendo no saldo")
    void mesmoPedidoDuasVezes() throws Exception {
        enviar(segunda, 15, null).andExpect(status().isCreated());

        enviar(segunda, 15, null)                    // sobram 15 dias: só a regra nova segura
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CRONA-01')].resultado").value("IMPEDIMENTO"));

        mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    @DisplayName("CRONA-01: um pedido que só encosta no outro (começa no dia seguinte) passa")
    void pedidoQueSoEncosta() throws Exception {
        // Duas segundas sem feriado perto (CLT-05), com 2 semanas entre elas.
        LocalDate inicio = segunda;
        while (!feriadoRepository.datasEntre(empresaDemo, inicio.plusWeeks(2), inicio.plusWeeks(2).plusDays(2)).isEmpty()) {
            inicio = segundaLivreDepoisDe(inicio.plusWeeks(1));
        }
        enviar(inicio, 14, null).andExpect(status().isCreated());        // de segunda até o domingo da semana seguinte

        enviar(inicio.plusWeeks(2), 5, null).andExpect(status().isCreated());   // começa na segunda logo depois
    }

    @Test
    @DisplayName("CRONA-01: depois de cancelar, as mesmas datas podem ser pedidas de novo")
    void pedirDeNovoDepoisDeCancelar() throws Exception {
        Long id = idDe(enviar(segunda, 15, null).andExpect(status().isCreated()));
        mockMvc.perform(post("/minhas-ferias/solicitacoes/" + id + "/cancelar").with(como(biaId, "COLABORADOR")))
                .andExpect(status().isOk());

        enviar(segunda, 15, null).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("ponto de atenção não impede o envio (menos de 30 dias de antecedência)")
    void atencaoNaoImpede() throws Exception {
        LocalDate embreve = segundaLivreDepoisDe(LocalDate.now(RelogioConfig.FUSO).plusDays(10));

        enviar(embreve, 15, null).andExpect(status().isCreated());
    }

    @Test
    @DisplayName("cancelar o próprio pedido: CANCELADA e os dias voltam para o saldo")
    void cancelar() throws Exception {
        Long id = idDe(enviar(segunda, 15, null));

        mockMvc.perform(post("/minhas-ferias/solicitacoes/" + id + "/cancelar").with(como(biaId, "COLABORADOR")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADA"));
        mockMvc.perform(get("/minhas-ferias/periodos-aquisitivos").with(como(biaId, "COLABORADOR")))
                .andExpect(jsonPath("$[0].livres").value(30));
    }

    @Test
    @DisplayName("ninguém cancela o pedido de outra pessoa: 404")
    void cancelarDeOutraPessoa() throws Exception {
        Long id = idDe(enviar(segunda, 15, null));
        Long ana = jdbc.queryForObject("SELECT id FROM colaborador WHERE email = 'ana.lima@demo.crona'", Long.class);

        mockMvc.perform(post("/minhas-ferias/solicitacoes/" + id + "/cancelar").with(como(ana, "COLABORADOR")))
                .andExpect(status().isNotFound());
        assertThat(jdbc.queryForObject("SELECT status FROM solicitacao_ferias WHERE id = ?", String.class, id))
                .isEqualTo("AGUARDANDO_GESTOR");
    }

    @Test
    @DisplayName("sem data de início: 400")
    void semInicio() throws Exception {
        mockMvc.perform(post("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR"))
                        .contentType(MediaType.APPLICATION_JSON).content("""
                                {"diasGozo": 15}
                                """))
                .andExpect(status().isBadRequest());
    }
}
