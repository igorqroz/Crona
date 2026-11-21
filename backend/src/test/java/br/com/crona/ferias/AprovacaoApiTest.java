package br.com.crona.ferias;

import br.com.crona.compartilhado.RelogioConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O lado do gestor e do RH, de ponta a ponta. A Bia (ComBiaTestBase) tem o Roberto como gestor.
 */
class AprovacaoApiTest extends ComBiaTestBase {

    /** A Bia envia um pedido de verdade pela API e o teste recebe o id. */
    private Long biaPede(LocalDate inicio, int dias) throws Exception {
        var json = mockMvc.perform(post("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inicio": "%s", "diasGozo": %d}
                                """.formatted(inicio, dias)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /** Coloca a Ana de férias no período (direto no banco, sem passar pelas regras). */
    private void anaForaEm(LocalDate inicio, int dias, String status) {
        Long ana = idDoColaborador("ana.lima@demo.crona");
        Long periodo = jdbc.queryForObject(
                "SELECT id FROM periodo_aquisitivo WHERE colaborador_id = ? AND numero = 2", Long.class, ana);
        jdbc.update("""
                INSERT INTO solicitacao_ferias (colaborador_id, periodo_aquisitivo_id, data_inicio, data_fim, dias_gozo, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, ana, periodo, inicio, inicio.plusDays(dias - 1L), dias, status);
    }

    private String json(boolean mesmoAssim, String mensagem) {
        return """
                {"mesmoAssim": %s, "mensagem": %s}
                """.formatted(mesmoAssim, mensagem == null ? "null" : "\"" + mensagem + "\"");
    }

    @Nested
    @DisplayName("aprovar")
    class Aprovar {

        @Test
        @DisplayName("o Roberto vê o pedido da Bia na fila, sem conflito, e aprova: APROVADA")
        void aprovarSemConflito() throws Exception {
            Long pedido = biaPede(segunda, 15);

            mockMvc.perform(get("/aprovacoes").with(como(robertoId, "GESTOR")))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].situacao").value("SEM_CONFLITO"));

            mockMvc.perform(post("/aprovacoes/" + pedido + "/aprovar").with(como(robertoId, "GESTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APROVADA"))
                    .andExpect(jsonPath("$.decididoPor.nome").value("Roberto Neves"));

            mockMvc.perform(get("/minhas-ferias/periodos-aquisitivos").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].aprovados").value(15));
        }

        @Test
        @DisplayName("com ponto de atenção: sem 'mesmo assim' é 422; sem motivo é 422; com os dois, aprova")
        void aprovarMesmoAssim() throws Exception {
            Long pedido = biaPede(segunda, 15);
            anaForaEm(segunda.plusDays(2), 5, "APROVADA");      // a Engenharia passa do limite (EMP-01)
            String url = "/aprovacoes/" + pedido + "/aprovar";

            mockMvc.perform(get("/aprovacoes/" + pedido).with(como(robertoId, "GESTOR")))
                    .andExpect(jsonPath("$.pedido.situacao").value("COM_ATENCAO"))
                    .andExpect(jsonPath("$.colegasNoPeriodo[0].nome").value("Ana Lima"));

            mockMvc.perform(post(url).with(como(robertoId, "GESTOR")))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.verificacoes[?(@.regra == 'EMP-01')].resultado").value("ATENCAO"));

            mockMvc.perform(post(url).with(como(robertoId, "GESTOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(true, null)))
                    .andExpect(status().isUnprocessableContent());

            mockMvc.perform(post(url).with(como(robertoId, "GESTOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(true, "Cliente pediu entrega antes")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APROVADA"))
                    .andExpect(jsonPath("$.mensagemDecisao").value("Cliente pediu entrega antes"));
        }

        @Test
        @DisplayName("com a confirmação do RH ligada (EMP-06): gestor aprova → RH confirma → APROVADA")
        void comConfirmacaoDoRh() throws Exception {
            jdbc.update("UPDATE configuracao_ferias SET exige_confirmacao_rh = TRUE WHERE empresa_id = ?", empresaDemo);
            Long pedido = biaPede(segunda, 15);
            Long rafael = idDoColaborador("rafael.costa@demo.crona");

            mockMvc.perform(post("/aprovacoes/" + pedido + "/aprovar").with(como(robertoId, "GESTOR")))
                    .andExpect(jsonPath("$.status").value("AGUARDANDO_RH"));

            mockMvc.perform(get("/confirmacoes-rh").with(como(rafael, "ADMIN")))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")]", hasSize(1)));

            mockMvc.perform(post("/confirmacoes-rh/" + pedido + "/confirmar").with(como(rafael, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APROVADA"))
                    .andExpect(jsonPath("$.decididoPor.nome").value("Rafael Costa"));
        }

        @Test
        @DisplayName("'aprovar os sem conflito' aprova só os que não têm alerta")
        void aprovarOsSemConflito() throws Exception {
            Long limpo = biaPede(segunda, 15);
            // Pedido da Ana daqui a poucos dias: menos de 30 de antecedência → ponto de atenção.
            LocalDate embreve = segundaLivreDepoisDe(LocalDate.now(RelogioConfig.FUSO).plusDays(10));
            anaForaEm(embreve, 10, "AGUARDANDO_GESTOR");

            mockMvc.perform(post("/aprovacoes/aprovar-sem-conflito").with(como(robertoId, "GESTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].id").value(limpo));

            mockMvc.perform(get("/aprovacoes").with(como(robertoId, "GESTOR")).param("filtro", "COM_CONFLITO"))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].colaborador.nome").value("Ana Lima"));
        }
    }

    @Nested
    @DisplayName("recusar")
    class Recusar {

        @Test
        @DisplayName("recusar sem motivo: 422")
        void semMotivo() throws Exception {
            Long pedido = biaPede(segunda, 15);

            mockMvc.perform(post("/aprovacoes/" + pedido + "/recusar").with(como(robertoId, "GESTOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(false, " ")))
                    .andExpect(status().isUnprocessableContent());
        }

        @Test
        @DisplayName("recusar com motivo: RECUSADA, e os dias voltam para o saldo da Bia")
        void comMotivo() throws Exception {
            Long pedido = biaPede(segunda, 15);

            mockMvc.perform(post("/aprovacoes/" + pedido + "/recusar").with(como(robertoId, "GESTOR"))
                            .contentType(MediaType.APPLICATION_JSON).content(json(false, "Entrega do projeto nessa semana")))
                    .andExpect(jsonPath("$.status").value("RECUSADA"));

            mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].mensagemDecisao").value("Entrega do projeto nessa semana"));
            mockMvc.perform(get("/minhas-ferias/periodos-aquisitivos").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].livres").value(30));
        }
    }

    @Nested
    @DisplayName("quem pode decidir")
    class QuemPode {

        @Test
        @DisplayName("um gestor de OUTRA equipe não vê nem aprova o pedido da Bia (404)")
        void outroGestor() throws Exception {
            Long pedido = biaPede(segunda, 15);
            Long marina = idDoColaborador("marina.jorge@demo.crona");
            jdbc.update("UPDATE colaborador SET perfil = 'GESTOR' WHERE id = ?", marina);

            mockMvc.perform(get("/aprovacoes").with(como(marina, "GESTOR")))
                    .andExpect(jsonPath("$", hasSize(0)));
            mockMvc.perform(post("/aprovacoes/" + pedido + "/aprovar").with(como(marina, "GESTOR")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("o ADMIN (RH) vê os pedidos da empresa toda")
        void admin() throws Exception {
            Long pedido = biaPede(segunda, 15);
            Long rafael = idDoColaborador("rafael.costa@demo.crona");

            mockMvc.perform(get("/aprovacoes").with(como(rafael, "ADMIN")))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")]", hasSize(1)));
        }

        @Test
        @DisplayName("COLABORADOR não acessa a fila de aprovação (403)")
        void colaborador() throws Exception {
            mockMvc.perform(get("/aprovacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("GESTOR não acessa a fila do RH (403)")
        void gestorNaFilaDoRh() throws Exception {
            mockMvc.perform(get("/confirmacoes-rh").with(como(robertoId, "GESTOR")))
                    .andExpect(status().isForbidden());
        }
    }
}
