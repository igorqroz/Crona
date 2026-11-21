package br.com.crona.ferias;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /minhas-ferias/calendario: as "barrinhas" da tela Solicitar férias (Fase 7b). */
class CalendarioApiTest extends ComBiaTestBase {

    /** Coloca a Ana (Engenharia, como a Bia) de férias, direto no banco. */
    private void anaForaEm(LocalDate inicio, int dias, String status) {
        Long ana = idDoColaborador("ana.lima@demo.crona");
        Long periodo = jdbc.queryForObject(
                "SELECT id FROM periodo_aquisitivo WHERE colaborador_id = ? AND numero = 2", Long.class, ana);
        jdbc.update("""
                INSERT INTO solicitacao_ferias (colaborador_id, periodo_aquisitivo_id, data_inicio, data_fim, dias_gozo, status)
                VALUES (?, ?, ?, ?, ?, ?)
                """, ana, periodo, inicio, inicio.plusDays(dias - 1L), dias, status);
    }

    @Test
    @DisplayName("mostra o departamento, o limite, o gestor e as férias dos colegas no intervalo")
    void calendario() throws Exception {
        anaForaEm(segunda, 10, "APROVADA");
        anaForaEm(segunda.plusMonths(6), 10, "APROVADA");     // fora do intervalo: não aparece

        mockMvc.perform(get("/minhas-ferias/calendario").with(como(biaId, "COLABORADOR"))
                        .param("de", segunda.minusDays(10).toString())
                        .param("ate", segunda.plusDays(50).toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departamento").value("Engenharia"))
                .andExpect(jsonPath("$.limite").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.gestor.nome").value("Roberto Neves"))
                .andExpect(jsonPath("$.ausencias", hasSize(1)))
                .andExpect(jsonPath("$.ausencias[0].nome").value("Ana Lima"))
                .andExpect(jsonPath("$.ausencias[0].inicio").value(segunda.toString()))
                .andExpect(jsonPath("$.ausencias[0].pendente").value(false));
    }

    @Test
    @DisplayName("pedidos em análise também aparecem, marcados como pendentes")
    void pendentes() throws Exception {
        anaForaEm(segunda, 10, "AGUARDANDO_GESTOR");

        mockMvc.perform(get("/minhas-ferias/calendario").with(como(biaId, "COLABORADOR"))
                        .param("de", segunda.toString()).param("ate", segunda.plusDays(30).toString()))
                .andExpect(jsonPath("$.ausencias[0].pendente").value(true));
    }

    @Test
    @DisplayName("traz os feriados do intervalo (Natal e Ano-Novo em dez/jan)")
    void feriados() throws Exception {
        int ano = LocalDate.now().getYear();

        mockMvc.perform(get("/minhas-ferias/calendario").with(como(biaId, "COLABORADOR"))
                        .param("de", ano + "-12-01").param("ate", (ano + 1) + "-01-31"))
                .andExpect(jsonPath("$.feriados", hasItem(ano + "-12-25")))
                .andExpect(jsonPath("$.feriados", hasItem((ano + 1) + "-01-01")));
    }

    @Test
    @DisplayName("intervalo invertido ou grande demais: 400")
    void intervaloInvalido() throws Exception {
        mockMvc.perform(get("/minhas-ferias/calendario").with(como(biaId, "COLABORADOR"))
                        .param("de", "2027-02-01").param("ate", "2027-01-01"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/minhas-ferias/calendario").with(como(biaId, "COLABORADOR"))
                        .param("de", "2027-01-01").param("ate", "2027-12-31"))
                .andExpect(status().isBadRequest());
    }
}
