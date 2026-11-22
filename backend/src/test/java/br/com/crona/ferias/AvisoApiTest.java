package br.com.crona.ferias;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** GET /minhas-ferias/solicitacoes/{id}/aviso: o aviso de férias em PDF (Fase 8a). */
class AvisoApiTest extends ComBiaTestBase {

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

    @Test
    @DisplayName("férias aprovadas: devolve o PDF, para baixar com o nome certo")
    void pdf() throws Exception {
        Long pedido = biaPede();
        mockMvc.perform(post("/aprovacoes/" + pedido + "/aprovar").with(como(robertoId, "GESTOR")))
                .andExpect(status().isOk());

        byte[] pdf = mockMvc.perform(get("/minhas-ferias/solicitacoes/" + pedido + "/aviso").with(como(biaId, "COLABORADOR")))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment; filename=\"aviso-de-ferias-" + segunda + ".pdf\"")))
                .andReturn().getResponse().getContentAsByteArray();

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    @DisplayName("pedido ainda em análise: 422 (o aviso só sai depois da aprovação)")
    void emAnalise() throws Exception {
        Long pedido = biaPede();

        mockMvc.perform(get("/minhas-ferias/solicitacoes/" + pedido + "/aviso").with(como(biaId, "COLABORADOR")))
                .andExpect(status().isUnprocessableContent());
    }

    @Test
    @DisplayName("o aviso de outra pessoa: 404")
    void deOutraPessoa() throws Exception {
        Long pedido = biaPede();
        mockMvc.perform(post("/aprovacoes/" + pedido + "/aprovar").with(como(robertoId, "GESTOR")));

        mockMvc.perform(get("/minhas-ferias/solicitacoes/" + pedido + "/aviso")
                        .with(como(idDoColaborador("ana.lima@demo.crona"), "COLABORADOR")))
                .andExpect(status().isNotFound());
    }
}
