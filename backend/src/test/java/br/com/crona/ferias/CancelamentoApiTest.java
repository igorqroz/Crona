package br.com.crona.ferias;

import br.com.crona.compartilhado.RelogioConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * O RH cancela férias que já estavam aprovadas (regras-de-negocio.md, seção 4,
 * "Cancelamento de férias aprovadas"):
 *   GET  /ferias-aprovadas
 *   POST /ferias-aprovadas/{id}/cancelar   {"motivo"}
 *
 * A Bia (ComBiaTestBase) tem o Roberto como gestor. Quem cancela é o Rafael (RH, perfil ADMIN).
 * As regras do próprio pedido (datas, motivo, status) já são testadas no SolicitacaoFeriasTest.
 * Aqui o foco é a API: permissões, os efeitos no resto do sistema e os códigos de resposta.
 */
class CancelamentoApiTest extends ComBiaTestBase {

    private Long rafaelId;
    private LocalDate hoje;

    @BeforeEach
    void prepararRh() {
        rafaelId = idDoColaborador("rafael.costa@demo.crona");
        hoje = LocalDate.now(RelogioConfig.FUSO);
    }

    // ------------------------------------------------------------------ auxiliares

    /** A Bia pede férias de 15 dias a partir de {@code inicio}, e o Roberto aprova. */
    private Long feriasAprovadasDaBia(LocalDate inicio) throws Exception {
        var json = mockMvc.perform(post("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inicio": "%s", "diasGozo": 15}
                                """.formatted(inicio)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long pedido = ((Number) JsonPath.read(json, "$.id")).longValue();
        mockMvc.perform(post("/aprovacoes/" + pedido + "/aprovar").with(como(robertoId, "GESTOR")))
                .andExpect(jsonPath("$.status").value("APROVADA"));
        return pedido;
    }

    /**
     * Férias APROVADAS da Bia começando em {@code inicio}, gravadas direto no banco. Serve para
     * datas que a API não deixaria pedir agora (amanhã, hoje), por causa da antecedência mínima.
     */
    private Long feriasAprovadasDaBiaNoBanco(LocalDate inicio) {
        Long periodo = jdbc.queryForObject(
                "SELECT id FROM periodo_aquisitivo WHERE colaborador_id = ? ORDER BY numero LIMIT 1", Long.class, biaId);
        return jdbc.queryForObject("""
                INSERT INTO solicitacao_ferias (colaborador_id, periodo_aquisitivo_id, data_inicio, data_fim, dias_gozo,
                                                status, gestor_decidiu_por_id)
                VALUES (?, ?, ?, ?, 5, 'APROVADA', ?) RETURNING id
                """, Long.class, biaId, periodo, inicio, inicio.plusDays(4), robertoId);
    }

    private ResultActions cancelar(Long pedido, Long quem, String perfil, String motivo) throws Exception {
        return mockMvc.perform(post("/ferias-aprovadas/" + pedido + "/cancelar").with(como(quem, perfil))
                .contentType(MediaType.APPLICATION_JSON)
                .content(motivo == null ? "{}" : """
                        {"motivo": "%s"}
                        """.formatted(motivo)));
    }

    private ResultActions rafaelCancela(Long pedido, String motivo) throws Exception {
        return cancelar(pedido, rafaelId, "ADMIN", motivo);
    }

    private String notificacoesDe(Long quem, String perfil) throws Exception {
        return mockMvc.perform(get("/notificacoes").with(como(quem, perfil)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    // ------------------------------------------------------------------ testes

    @Nested
    @DisplayName("quem pode")
    class QuemPode {

        @Test
        @DisplayName("gestor e colaborador recebem 403, na lista e no cancelamento, e as férias continuam aprovadas")
        void soORh() throws Exception {
            Long pedido = feriasAprovadasDaBia(segunda);

            for (var pessoa : new Object[][] {{robertoId, "GESTOR"}, {biaId, "COLABORADOR"}}) {
                Long quem = (Long) pessoa[0];
                String perfil = (String) pessoa[1];
                mockMvc.perform(get("/ferias-aprovadas").with(como(quem, perfil))).andExpect(status().isForbidden());
                cancelar(pedido, quem, perfil, "Tentativa").andExpect(status().isForbidden());
            }

            mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].status").value("APROVADA"));
        }

        @Test
        @DisplayName("ninguém cancela as próprias férias: a lista explica o bloqueio, e o POST dá 422")
        void propriasFerias() throws Exception {
            Long pedido = feriasAprovadasDaBia(segunda);

            // A Bia, aqui como RH (ADMIN), vê as próprias férias na lista, mas bloqueadas.
            mockMvc.perform(get("/ferias-aprovadas").with(como(biaId, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].bloqueio", hasItem(containsString("suas férias"))));
            cancelar(pedido, biaId, "ADMIN", "Quero desistir").andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    @DisplayName("a lista do RH (GET /ferias-aprovadas)")
    class Lista {

        @Test
        @DisplayName("mostra as aprovadas que ainda vão começar, com quem aprovou e sem bloqueio")
        void aprovadas() throws Exception {
            Long pedido = feriasAprovadasDaBia(segunda);

            mockMvc.perform(get("/ferias-aprovadas").with(como(rafaelId, "ADMIN")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].colaborador.nome").value("Bia Teste"))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].departamento").value("Engenharia"))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].inicio").value(segunda.toString()))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].diasGozo").value(15))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].aprovadoPor.nome").value("Roberto Neves"))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].bloqueio", hasItem(org.hamcrest.Matchers.nullValue())));
        }

        @Test
        @DisplayName("não mostra pedidos em análise, férias que começam hoje nem as já canceladas")
        void soAsQueDaParaCancelar() throws Exception {
            Long emAnalise = ((Number) JsonPath.read(mockMvc.perform(post("/minhas-ferias/solicitacoes")
                            .with(como(biaId, "COLABORADOR")).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"inicio": "%s", "diasGozo": 15}
                                    """.formatted(segunda)))
                    .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
            Long comecaHoje = feriasAprovadasDaBiaNoBanco(hoje);
            Long amanha = feriasAprovadasDaBiaNoBanco(hoje.plusDays(1));

            mockMvc.perform(get("/ferias-aprovadas").with(como(rafaelId, "ADMIN")))
                    .andExpect(jsonPath("$[*].id", not(hasItem(emAnalise.intValue()))))
                    .andExpect(jsonPath("$[*].id", not(hasItem(comecaHoje.intValue()))))
                    .andExpect(jsonPath("$[*].id", hasItem(amanha.intValue()))); // véspera: ainda dá

            rafaelCancela(amanha, "Aprovado por engano").andExpect(status().isOk());
            mockMvc.perform(get("/ferias-aprovadas").with(como(rafaelId, "ADMIN")))
                    .andExpect(jsonPath("$[*].id", not(hasItem(amanha.intValue()))));
        }
    }

    @Nested
    @DisplayName("cancelar (POST /ferias-aprovadas/{id}/cancelar)")
    class Cancelar {

        @Test
        @DisplayName("CANCELADA, com quem cancelou e o motivo, e os 15 dias voltam para o saldo")
        void cancelaEDevolveOSaldo() throws Exception {
            Long pedido = feriasAprovadasDaBia(segunda);
            mockMvc.perform(get("/minhas-ferias/periodos-aquisitivos").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].aprovados").value(15))
                    .andExpect(jsonPath("$[0].livres").value(15));

            rafaelCancela(pedido, "Aprovado por engano: era para a outra Bia")
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("CANCELADA"))
                    .andExpect(jsonPath("$.canceladoPor.nome").value("Rafael Costa"))
                    .andExpect(jsonPath("$.motivoCancelamento").value("Aprovado por engano: era para a outra Bia"));

            mockMvc.perform(get("/minhas-ferias/periodos-aquisitivos").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].aprovados").value(0))
                    .andExpect(jsonPath("$[0].livres").value(30));
            assertThat(jdbc.queryForObject("SELECT cancelado_em IS NOT NULL FROM solicitacao_ferias WHERE id = ?",
                    Boolean.class, pedido)).isTrue();
        }

        @Test
        @DisplayName("na véspera do início ainda dá para cancelar; no dia do início, 422")
        void limiteDaVespera() throws Exception {
            Long amanha = feriasAprovadasDaBiaNoBanco(hoje.plusDays(1));
            Long comecaHoje = feriasAprovadasDaBiaNoBanco(hoje);

            rafaelCancela(amanha, "Engano").andExpect(status().isOk());
            rafaelCancela(comecaHoje, "Engano")
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value(containsString("véspera")));
        }

        @Test
        @DisplayName("o aviso em PDF deixa de sair (422) e as datas ficam livres para um pedido novo (CRONA-01)")
        void avisoEDatas() throws Exception {
            Long pedido = feriasAprovadasDaBia(segunda);
            rafaelCancela(pedido, "Datas erradas").andExpect(status().isOk());

            mockMvc.perform(get("/minhas-ferias/solicitacoes/" + pedido + "/aviso").with(como(biaId, "COLABORADOR")))
                    .andExpect(status().isUnprocessableContent());
            mockMvc.perform(post("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"inicio": "%s", "diasGozo": 15}
                                    """.formatted(segunda)))
                    .andExpect(status().isCreated());
        }

        @Test
        @DisplayName("a Bia e o Roberto são avisados, os dois com o motivo")
        void notificacoes() throws Exception {
            Long pedido = feriasAprovadasDaBia(segunda);

            rafaelCancela(pedido, "Aprovado por engano").andExpect(status().isOk());

            assertThat(notificacoesDe(biaId, "COLABORADOR"))
                    .contains("Rafael Costa cancelou suas férias")
                    .contains("Aprovado por engano")
                    .contains("Os dias voltaram para o seu saldo.");
            assertThat(notificacoesDe(robertoId, "GESTOR"))
                    .contains("Rafael Costa cancelou as férias aprovadas de Bia Teste")
                    .contains("Aprovado por engano");
        }

        @Test
        @DisplayName("sem motivo (campo ausente, vazio ou só espaços): 422, e nada muda")
        void semMotivo() throws Exception {
            Long pedido = feriasAprovadasDaBia(segunda);

            rafaelCancela(pedido, null).andExpect(status().isUnprocessableContent());
            rafaelCancela(pedido, "").andExpect(status().isUnprocessableContent());
            rafaelCancela(pedido, "   ").andExpect(status().isUnprocessableContent());

            mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[?(@.id == " + pedido + ")].status").value("APROVADA"));
        }

        @Test
        @DisplayName("motivo com mais de 500 caracteres: 400")
        void motivoGrandeDemais() throws Exception {
            Long pedido = feriasAprovadasDaBia(segunda);

            rafaelCancela(pedido, "x".repeat(501)).andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("cancelar duas vezes: 422 na segunda, e só saem as notificações da primeira")
        void duasVezes() throws Exception {
            Long pedido = feriasAprovadasDaBia(segunda);
            rafaelCancela(pedido, "Primeiro motivo").andExpect(status().isOk());
            int notificacoesDaBia = jdbc.queryForObject(
                    "SELECT count(*) FROM notificacao WHERE destinatario_id = ?", Integer.class, biaId);

            rafaelCancela(pedido, "Segundo motivo").andExpect(status().isUnprocessableContent());

            assertThat(jdbc.queryForObject("SELECT count(*) FROM notificacao WHERE destinatario_id = ?",
                    Integer.class, biaId)).isEqualTo(notificacoesDaBia);
            assertThat(jdbc.queryForObject("SELECT motivo_cancelamento FROM solicitacao_ferias WHERE id = ?",
                    String.class, pedido)).isEqualTo("Primeiro motivo");
        }

        @Test
        @DisplayName("pedido que não existe: 404")
        void naoExiste() throws Exception {
            rafaelCancela(999_999L, "Engano").andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("pedido ainda em análise não se cancela por aqui: 422")
        void emAnalise() throws Exception {
            Long emAnalise = ((Number) JsonPath.read(mockMvc.perform(post("/minhas-ferias/solicitacoes")
                            .with(como(biaId, "COLABORADOR")).contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"inicio": "%s", "diasGozo": 15}
                                    """.formatted(segunda)))
                    .andReturn().getResponse().getContentAsString(), "$.id")).longValue();

            rafaelCancela(emAnalise, "Engano").andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    @DisplayName("troca em aberto")
    class TrocaEmAberto {

        @Test
        @DisplayName("férias numa troca em aberto: a lista mostra o bloqueio, e o cancelamento dá 409")
        void bloqueia() throws Exception {
            // O Caio, da mesma equipe, também tem férias aprovadas; a Bia propõe trocar com ele.
            Long engenharia = jdbc.queryForObject(
                    "SELECT id FROM departamento WHERE nome = 'Engenharia' AND empresa_id = ?", Long.class, empresaDemo);
            Long caioId = ((Number) JsonPath.read(mockMvc.perform(post("/colaboradores")
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"nome": "Caio Teste", "email": "caio.teste@demo.crona", "senha": "segredo123",
                                     "perfil": "COLABORADOR", "dataAdmissao": "%s", "departamentoId": %d, "gestorId": %d}
                                    """.formatted(hoje.minusMonths(18), engenharia, robertoId)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString(), "$.id")).longValue();
            LocalDate depois = segundaLivreDepoisDe(segunda.plusWeeks(5));

            Long daBia = feriasAprovadasDaBia(segunda);
            var json = mockMvc.perform(post("/minhas-ferias/solicitacoes").with(como(caioId, "COLABORADOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"inicio": "%s", "diasGozo": 15}
                                    """.formatted(depois)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Long doCaio = ((Number) JsonPath.read(json, "$.id")).longValue();
            mockMvc.perform(post("/aprovacoes/" + doCaio + "/aprovar").with(como(robertoId, "GESTOR")))
                    .andExpect(status().isOk());
            mockMvc.perform(post("/minhas-ferias/negociacoes").with(como(biaId, "COLABORADOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"pedidoDoColegaId": %d, "pedidoDoSolicitanteId": %d,
                                     "inicioSolicitante": "%s", "inicioColega": "%s"}
                                    """.formatted(doCaio, daBia, depois, segunda)))
                    .andExpect(status().isCreated());

            mockMvc.perform(get("/ferias-aprovadas").with(como(rafaelId, "ADMIN")))
                    .andExpect(jsonPath("$[?(@.id == " + daBia + ")].bloqueio", hasItem(containsString("troca"))))
                    .andExpect(jsonPath("$[?(@.id == " + doCaio + ")].bloqueio", hasItem(containsString("troca"))));
            rafaelCancela(daBia, "Engano")
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(containsString("Encerre a troca antes")));
            rafaelCancela(doCaio, "Engano").andExpect(status().isConflict());
        }
    }
}
