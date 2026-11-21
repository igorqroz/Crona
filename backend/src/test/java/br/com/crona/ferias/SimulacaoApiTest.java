package br.com.crona.ferias;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A simulação de ponta a ponta, com o banco de verdade e a Bia (ver ComBiaTestBase). */
class SimulacaoApiTest extends ComBiaTestBase {

    private ResultActions simular(LocalDate inicio, int diasGozo) throws Exception {
        return mockMvc.perform(post("/minhas-ferias/simulacao").with(como(biaId, "COLABORADOR"))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"inicio": "%s", "diasGozo": %d}
                        """.formatted(inicio, diasGozo)));
    }

    @Test
    @DisplayName("pedido sem problemas: pode enviar, datas calculadas e 'Tudo certo para enviar.'")
    void pedidoLimpo() throws Exception {
        simular(segunda, 15)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.podeEnviar").value(true))
                .andExpect(jsonPath("$.resumo").value("Tudo certo para enviar."))
                .andExpect(jsonPath("$.fim").value(segunda.plusDays(14).toString()))
                .andExpect(jsonPath("$.periodo").value(1))
                .andExpect(jsonPath("$.livresNoPeriodo").value(30))
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CLT-05')].resultado").value("OK"));
    }

    @Test
    @DisplayName("começar numa quinta: impedimento da CLT-05 e não pode enviar")
    void comecaNaQuinta() throws Exception {
        simular(segunda.plusDays(3), 15)
                .andExpect(jsonPath("$.podeEnviar").value(false))
                .andExpect(jsonPath("$.resumo").value("1 impedimento: ajuste as datas para poder enviar."))
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CLT-05')].resultado").value("IMPEDIMENTO"));
    }

    @Test
    @DisplayName("feriado da empresa na terça: começar na segunda vira impedimento")
    void feriadoDaEmpresa() throws Exception {
        jdbc.update("INSERT INTO feriado (empresa_id, data, descricao) VALUES (?, ?, 'Aniversário da cidade')",
                empresaDemo, segunda.plusDays(1));

        simular(segunda, 15)
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CLT-05')].resultado").value("IMPEDIMENTO"));
    }

    @Test
    @DisplayName("colega do departamento no período: EMP-01 vira ponto de atenção e o colega aparece na lista")
    void colegaNoPeriodo() throws Exception {
        // Engenharia tem 6 pessoas com a Bia; 70% trabalhando → limite de 1 fora por dia.
        Long ana = jdbc.queryForObject("SELECT id FROM colaborador WHERE email = 'ana.lima@demo.crona'", Long.class);
        Long periodoDaAna = jdbc.queryForObject(
                "SELECT id FROM periodo_aquisitivo WHERE colaborador_id = ? AND numero = 2", Long.class, ana);
        jdbc.update("""
                INSERT INTO solicitacao_ferias (colaborador_id, periodo_aquisitivo_id, data_inicio, data_fim, dias_gozo, status)
                VALUES (?, ?, ?, ?, 10, 'AGUARDANDO_GESTOR')
                """, ana, periodoDaAna, segunda.plusDays(2), segunda.plusDays(11));

        simular(segunda, 15)
                .andExpect(jsonPath("$.podeEnviar").value(true))
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'EMP-01')].resultado").value("ATENCAO"))
                .andExpect(jsonPath("$.colegasNoPeriodo[0].nome").value("Ana Lima"))
                .andExpect(jsonPath("$.colegasNoPeriodo[0].pendente").value(true));
    }

    @Test
    @DisplayName("período restrito do tipo BLOQUEIO: impedimento da EMP-05")
    void periodoRestrito() throws Exception {
        jdbc.update("""
                INSERT INTO periodo_restrito (empresa_id, tipo, data_inicio, data_fim, motivo)
                VALUES (?, 'BLOQUEIO', ?, ?, 'Inventário anual')
                """, empresaDemo, segunda.plusDays(5), segunda.plusDays(8));

        simular(segunda, 15)
                .andExpect(jsonPath("$.podeEnviar").value(false))
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'EMP-05')].mensagem")
                        .value(hasItem(containsString("Inventário anual"))));
    }

    @Test
    @DisplayName("dados inválidos (0 dias): 400")
    void dadosInvalidos() throws Exception {
        simular(segunda, 0).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("simular não grava nada no banco")
    void naoGrava() throws Exception {
        Integer antes = jdbc.queryForObject("SELECT count(*) FROM solicitacao_ferias", Integer.class);

        simular(segunda, 15).andExpect(status().isOk());

        assertThat(jdbc.queryForObject("SELECT count(*) FROM solicitacao_ferias", Integer.class)).isEqualTo(antes);
    }
}
