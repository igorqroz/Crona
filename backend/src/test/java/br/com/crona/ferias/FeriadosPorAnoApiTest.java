package br.com.crona.ferias;

import br.com.crona.compartilhado.RelogioConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.MediaType;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * BUG-08 (registro em docs/plano-de-testes.md): o Crona só tinha os feriados de 2026 e 2027,
 * gravados na migração V2. Para datas de 2028 em diante, a CLT-05 deixava de ver os feriados.
 *
 * Os testes contam os anos a partir de HOJE, para não virarem outra bomba-relógio: não basta
 * cadastrar mais alguns anos fixos. E não dizem COMO os feriados são criados (migração, rotina
 * ou cálculo): só conferem o que a API responde.
 */
class FeriadosPorAnoApiTest extends ComBiaTestBase {

    /** Os feriados nacionais do calendário do Crona (os mesmos da V2; Carnaval e Corpus Christi ficam de fora). */
    private static List<String> nacionais(int ano) {
        return List.of(
                ano + "-01-01",
                sextaFeiraSanta(ano).toString(),
                ano + "-04-21",
                ano + "-05-01",
                ano + "-09-07",
                ano + "-10-12",
                ano + "-11-02",
                ano + "-11-15",
                ano + "-11-20",
                ano + "-12-25");
    }

    /** Dois dias antes da Páscoa (algoritmo de Meeus/Jones/Butcher, o do calendário gregoriano). */
    private static LocalDate sextaFeiraSanta(int ano) {
        int a = ano % 19;
        int b = ano / 100;
        int c = ano % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int mes = (h + l - 7 * m + 114) / 31;
        int dia = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(ano, mes, dia).minusDays(2);
    }

    @Test
    @DisplayName("guarda do próprio teste: a conta da Sexta-feira Santa bate com datas conhecidas")
    void contaDaPascoa() {
        assertThat(sextaFeiraSanta(2026)).isEqualTo(LocalDate.of(2026, 4, 3));
        assertThat(sextaFeiraSanta(2027)).isEqualTo(LocalDate.of(2027, 3, 26));
        assertThat(sextaFeiraSanta(2028)).isEqualTo(LocalDate.of(2028, 4, 14));
        assertThat(sextaFeiraSanta(2029)).isEqualTo(LocalDate.of(2029, 3, 30));
        assertThat(sextaFeiraSanta(2030)).isEqualTo(LocalDate.of(2030, 4, 19));
    }

    @ParameterizedTest(name = "BUG-08: daqui a {0} ano(s), o calendário traz os 10 feriados nacionais")
    @ValueSource(ints = {0, 1, 2, 3, 5})
    void feriadosDoAno(int anosAFrente) throws Exception {
        int ano = LocalDate.now(RelogioConfig.FUSO).getYear() + anosAFrente;

        // O calendário aceita no máximo 124 dias por vez: o ano em três pedaços.
        List<String> feriados = new ArrayList<>();
        for (String[] pedaco : new String[][] {{"01-01", "04-30"}, {"05-01", "08-31"}, {"09-01", "12-31"}}) {
            var json = mockMvc.perform(get("/minhas-ferias/calendario").with(como(biaId, "COLABORADOR"))
                            .param("de", ano + "-" + pedaco[0]).param("ate", ano + "-" + pedaco[1]))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            feriados.addAll(JsonPath.read(json, "$.feriados"));
        }

        assertThat(feriados).containsExactlyInAnyOrderElementsOf(nacionais(ano));
    }

    /*
     * O efeito na prática. Férias não podem começar nos 2 dias antes de um feriado (CLT-05).
     * A quarta-feira antes da Sexta-feira Santa só é proibida POR CAUSA do feriado (numa semana
     * comum, quarta pode). Daqui a 2 anos para a data estar sempre no futuro.
     */
    @Test
    @DisplayName("BUG-08: daqui a 2 anos, começar na quarta antes da Sexta-feira Santa é impedimento (CLT-05)")
    void clt05EnxergaOFeriado() throws Exception {
        LocalDate sexta = sextaFeiraSanta(LocalDate.now(RelogioConfig.FUSO).getYear() + 2);

        mockMvc.perform(post("/minhas-ferias/simulacao").with(como(biaId, "COLABORADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inicio": "%s", "diasGozo": 10}
                                """.formatted(sexta.minusDays(2))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CLT-05')].resultado").value("IMPEDIMENTO"));
    }
}
