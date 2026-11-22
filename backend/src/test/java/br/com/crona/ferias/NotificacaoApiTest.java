package br.com.crona.ferias;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Fase 8b: as notificações nascem dos eventos do fluxo, cada uma para a pessoa certa.
 * A Bia (ComBiaTestBase) tem o Roberto como gestor.
 */
class NotificacaoApiTest extends ComBiaTestBase {

    @Autowired
    private AvisoDeVencimento avisoDeVencimento;

    private Long biaPede() throws Exception {
        var json = mockMvc.perform(post("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inicio": "%s", "diasGozo": 15}
                                """.formatted(segunda)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    /** O "$" das notificações de alguém, já como texto, para os testes procurarem nele. */
    private String notificacoesDe(Long quem, String perfil) throws Exception {
        return mockMvc.perform(get("/notificacoes").with(como(quem, perfil)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private int quantasDe(Long quem) {
        return jdbc.queryForObject("SELECT count(*) FROM notificacao WHERE destinatario_id = ?", Integer.class, quem);
    }

    @Nested
    @DisplayName("pedidos de férias")
    class Pedidos {

        @Test
        @DisplayName("pedido enviado: o gestor é avisado, com o link para as aprovações")
        void enviado() throws Exception {
            biaPede();

            mockMvc.perform(get("/notificacoes").with(como(robertoId, "GESTOR")))
                    .andExpect(jsonPath("$.naoLidas").value(1))
                    .andExpect(jsonPath("$.itens[0].tipo").value("SOLICITACAO_PENDENTE"))
                    .andExpect(jsonPath("$.itens[0].mensagem").value(
                            org.hamcrest.Matchers.startsWith("Bia Teste pediu férias de")))
                    .andExpect(jsonPath("$.itens[0].link").value("/aprovacoes"))
                    .andExpect(jsonPath("$.itens[0].lida").value(false));
            assertThat(quantasDe(biaId)).as("quem pediu não é avisado do próprio pedido").isZero();
        }

        @Test
        @DisplayName("aprovado: a Bia é avisada; o Roberto não recebe nada da própria aprovação")
        void aprovado() throws Exception {
            Long pedido = biaPede();
            int doRobertoAntes = quantasDe(robertoId);

            mockMvc.perform(post("/aprovacoes/" + pedido + "/aprovar").with(como(robertoId, "GESTOR")))
                    .andExpect(status().isOk());

            mockMvc.perform(get("/notificacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$.itens[0].tipo").value("SOLICITACAO_DECIDIDA"))
                    .andExpect(jsonPath("$.itens[0].mensagem").value(
                            org.hamcrest.Matchers.startsWith("Roberto Neves aprovou suas férias de")));
            assertThat(quantasDe(robertoId)).isEqualTo(doRobertoAntes);
        }

        @Test
        @DisplayName("recusado: o aviso traz o motivo")
        void recusado() throws Exception {
            Long pedido = biaPede();
            mockMvc.perform(post("/aprovacoes/" + pedido + "/recusar").with(como(robertoId, "GESTOR"))
                    .contentType(MediaType.APPLICATION_JSON).content("{\"mensagem\": \"Entrega do projeto\"}"));

            assertThat(notificacoesDe(biaId, "COLABORADOR"))
                    .contains("Roberto Neves recusou suas férias de").contains("Entrega do projeto");
        }

        @Test
        @DisplayName("com a confirmação do RH ligada (EMP-06): a Bia sabe que falta o RH, e o RH é avisado")
        void comRh() throws Exception {
            jdbc.update("UPDATE configuracao_ferias SET exige_confirmacao_rh = TRUE WHERE empresa_id = ?", empresaDemo);
            Long pedido = biaPede();
            Long rafael = idDoColaborador("rafael.costa@demo.crona");

            mockMvc.perform(post("/aprovacoes/" + pedido + "/aprovar").with(como(robertoId, "GESTOR")));

            assertThat(notificacoesDe(biaId, "COLABORADOR")).contains("Agora falta a confirmação do RH");
            assertThat(notificacoesDe(rafael, "ADMIN")).contains("CONFIRMACAO_RH_PENDENTE").contains("Bia Teste");
        }
    }

    @Nested
    @DisplayName("trocas")
    class Trocas {

        @Test
        @DisplayName("proposta: o colega é avisado; aceita: quem propôs e o gestor são avisados")
        void propostaEAceite() throws Exception {
            // A Bia tem férias aprovadas; a Ana também (direto no banco, mais adiante).
            Long daBia = biaPede();
            mockMvc.perform(post("/aprovacoes/" + daBia + "/aprovar").with(como(robertoId, "GESTOR")));
            Long ana = idDoColaborador("ana.lima@demo.crona");
            LocalDate depois = segundaLivreDepoisDe(segunda.plusWeeks(5));
            Long periodoDaAna = jdbc.queryForObject(
                    "SELECT id FROM periodo_aquisitivo WHERE colaborador_id = ? AND numero = 2", Long.class, ana);
            Long daAna = jdbc.queryForObject("""
                    INSERT INTO solicitacao_ferias (colaborador_id, periodo_aquisitivo_id, data_inicio, data_fim, dias_gozo, status)
                    VALUES (?, ?, ?, ?, 15, 'APROVADA') RETURNING id
                    """, Long.class, ana, periodoDaAna, depois, depois.plusDays(14));

            var json = mockMvc.perform(post("/minhas-ferias/negociacoes").with(como(biaId, "COLABORADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"pedidoDoColegaId": %d, "pedidoDoSolicitanteId": %d,
                                     "inicioSolicitante": "%s", "inicioColega": "%s"}
                                    """.formatted(daAna, daBia, depois, segunda)))
                    .andExpect(status().isCreated())
                    .andReturn().getResponse().getContentAsString();
            Long negociacao = ((Number) JsonPath.read(json, "$.id")).longValue();

            assertThat(notificacoesDe(ana, "COLABORADOR"))
                    .contains("TROCA_RECEBIDA").contains("Bia Teste propôs trocar férias com você.");

            mockMvc.perform(post("/minhas-ferias/negociacoes/" + negociacao + "/aceitar").with(como(ana, "COLABORADOR")))
                    .andExpect(status().isOk());

            assertThat(notificacoesDe(biaId, "COLABORADOR")).contains("Ana Lima aceitou a troca de férias");
            assertThat(notificacoesDe(robertoId, "GESTOR")).contains("TROCA_PENDENTE")
                    .contains("A troca de férias entre Bia Teste e Ana Lima espera a sua aprovação.");
        }
    }

    @Nested
    @DisplayName("lidas e não lidas")
    class Lidas {

        @Test
        @DisplayName("marcar uma como lida, e depois todas")
        void marcar() throws Exception {
            biaPede();
            biaPede();      // dois pedidos → duas notificações para o Roberto
            Integer id = JsonPath.read(notificacoesDe(robertoId, "GESTOR"), "$.itens[0].id");

            mockMvc.perform(post("/notificacoes/" + id + "/lida").with(como(robertoId, "GESTOR")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/notificacoes").with(como(robertoId, "GESTOR")))
                    .andExpect(jsonPath("$.naoLidas").value(1))
                    .andExpect(jsonPath("$.itens[0].lida").value(true));

            mockMvc.perform(post("/notificacoes/lidas").with(como(robertoId, "GESTOR")))
                    .andExpect(status().isNoContent());
            mockMvc.perform(get("/notificacoes").with(como(robertoId, "GESTOR")))
                    .andExpect(jsonPath("$.naoLidas").value(0))
                    .andExpect(jsonPath("$.itens", hasSize(2)));
        }

        @Test
        @DisplayName("ninguém mexe na notificação de outra pessoa (404)")
        void deOutraPessoa() throws Exception {
            biaPede();
            Integer doRoberto = JsonPath.read(notificacoesDe(robertoId, "GESTOR"), "$.itens[0].id");

            mockMvc.perform(post("/notificacoes/" + doRoberto + "/lida").with(como(biaId, "COLABORADOR")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    @DisplayName("rotina diária: férias a vencer")
    class FeriasAVencer {

        @Test
        @DisplayName("faltando 50 dias para o prazo, avisa uma vez só, mesmo rodando duas vezes no dia")
        void avisaUmaVez() {
            LocalDate prazo = jdbc.queryForObject(
                    "SELECT data_limite_concessao FROM periodo_aquisitivo WHERE colaborador_id = ? AND numero = 1",
                    LocalDate.class, biaId);
            LocalDate hoje = prazo.minusDays(50);

            avisoDeVencimento.avisar(hoje);
            avisoDeVencimento.avisar(hoje);     // a API reiniciou, por exemplo

            assertThat(jdbc.queryForList(
                    "SELECT mensagem FROM notificacao WHERE destinatario_id = ? AND tipo = 'FERIAS_A_VENCER'",
                    String.class, biaId))
                    .singleElement().asString().contains("faltam 50 dias").contains("30 dias para marcar");
        }

        @Test
        @DisplayName("cada marco (90, 60, 30 dias) é um aviso novo")
        void marcos() {
            assertThat(AvisoDeVencimento.marco(120)).isNull();
            assertThat(AvisoDeVencimento.marco(90)).isEqualTo(90);
            assertThat(AvisoDeVencimento.marco(61)).isEqualTo(90);
            assertThat(AvisoDeVencimento.marco(60)).isEqualTo(60);
            assertThat(AvisoDeVencimento.marco(30)).isEqualTo(30);
            assertThat(AvisoDeVencimento.marco(1)).isEqualTo(30);
            assertThat(AvisoDeVencimento.marco(0)).as("o prazo já passou: aí é o 'vencido' da tela").isNull();
        }
    }
}
