package br.com.crona.ferias;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /gestao/calendario: quem está fora, para o gestor e o RH (Fase 8c). */
class CalendarioDaGestaoApiTest extends ComBiaTestBase {

    /** A Bia pede (fica em análise) e a Ana tem férias aprovadas no mesmo período. */
    private void cenario() throws Exception {
        mockMvc.perform(post("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inicio": "%s", "diasGozo": 15}
                                """.formatted(segunda)))
                .andExpect(status().isCreated());
        Long ana = idDoColaborador("ana.lima@demo.crona");
        Long periodo = jdbc.queryForObject(
                "SELECT id FROM periodo_aquisitivo WHERE colaborador_id = ? AND numero = 2", Long.class, ana);
        jdbc.update("""
                INSERT INTO solicitacao_ferias (colaborador_id, periodo_aquisitivo_id, data_inicio, data_fim, dias_gozo, status)
                VALUES (?, ?, ?, ?, 10, 'APROVADA')
                """, ana, periodo, segunda.plusDays(2), segunda.plusDays(11));
    }

    private org.springframework.test.web.servlet.ResultActions calendarioDe(Long quem, String perfil) throws Exception {
        return mockMvc.perform(get("/gestao/calendario").with(como(quem, perfil))
                .param("de", segunda.minusDays(7).toString())
                .param("ate", segunda.plusDays(35).toString()));
    }

    @Test
    @DisplayName("o gestor vê a equipe por departamento, com quem está fora, o limite e o que está pendente")
    void gestor() throws Exception {
        cenario();

        calendarioDe(robertoId, "GESTOR")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.departamentos", hasSize(1)))
                .andExpect(jsonPath("$.departamentos[0].nome").value("Engenharia"))
                .andExpect(jsonPath("$.departamentos[0].ativos").value(greaterThanOrEqualTo(6)))
                .andExpect(jsonPath("$.departamentos[0].limite").value(greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.departamentos[0].pessoas", hasSize(2)))
                .andExpect(jsonPath("$.departamentos[0].pessoas[?(@.nome == 'Bia Teste')].ausencias[0].pendente").value(true))
                .andExpect(jsonPath("$.departamentos[0].pessoas[?(@.nome == 'Ana Lima')].ausencias[0].pendente").value(false));
    }

    @Test
    @DisplayName("um gestor de outra equipe não vê ninguém da equipe do Roberto")
    void outraEquipe() throws Exception {
        cenario();
        Long marina = idDoColaborador("marina.jorge@demo.crona");
        jdbc.update("UPDATE colaborador SET perfil = 'GESTOR' WHERE id = ?", marina);

        calendarioDe(marina, "GESTOR").andExpect(jsonPath("$.departamentos", hasSize(0)));
    }

    @Test
    @DisplayName("o RH (ADMIN) vê a empresa toda")
    void rh() throws Exception {
        cenario();

        calendarioDe(idDoColaborador("rafael.costa@demo.crona"), "ADMIN")
                .andExpect(jsonPath("$.departamentos[?(@.nome == 'Engenharia')].pessoas[*].nome",
                        org.hamcrest.Matchers.hasItems("Bia Teste", "Ana Lima")));
    }

    @Test
    @DisplayName("traz os períodos restritos (EMP-05) do departamento e da empresa toda")
    void restricoes() throws Exception {
        cenario();
        Long engenharia = jdbc.queryForObject("SELECT id FROM departamento WHERE nome = 'Engenharia' AND empresa_id = ?",
                Long.class, empresaDemo);
        jdbc.update("""
                INSERT INTO periodo_restrito (empresa_id, departamento_id, tipo, data_inicio, data_fim, motivo)
                VALUES (?, ?, 'ALERTA', ?, ?, 'Entrega da versão')
                """, empresaDemo, engenharia, segunda.plusDays(14), segunda.plusDays(18));

        calendarioDe(robertoId, "GESTOR")
                .andExpect(jsonPath("$.restricoes[?(@.motivo == 'Entrega da versão')].departamentoId").value(engenharia.intValue()))
                .andExpect(jsonPath("$.restricoes[?(@.motivo == 'Entrega da versão')].bloqueia").value(false));
    }

    @Test
    @DisplayName("COLABORADOR não acessa (403); intervalo inválido é 400")
    void permissoesEValidacao() throws Exception {
        calendarioDe(biaId, "COLABORADOR").andExpect(status().isForbidden());

        mockMvc.perform(get("/gestao/calendario").with(como(robertoId, "GESTOR"))
                        .param("de", "2027-03-01").param("ate", "2027-01-01"))
                .andExpect(status().isBadRequest());
    }
}
