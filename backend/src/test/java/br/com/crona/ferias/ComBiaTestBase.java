package br.com.crona.ferias;

import br.com.crona.ApiTestBase;
import br.com.crona.compartilhado.RelogioConfig;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base dos testes de férias que precisam de um cenário que NÃO dependa da data de hoje.
 *
 * Cria a "Bia": colaboradora da Engenharia admitida há 18 meses. Assim ela sempre tem
 * 1 período completo, com 30 dias livres e uns 6 meses de prazo, em qualquer dia em que
 * os testes rodem. Tudo é desfeito no fim de cada teste (@Transactional da ApiTestBase).
 */
abstract class ComBiaTestBase extends ApiTestBase {

    @Autowired
    protected JdbcTemplate jdbc;
    @Autowired
    protected FeriadoRepository feriadoRepository;

    protected Long biaId;
    /** O gestor da Bia (o Roberto Neves dos dados de desenvolvimento). */
    protected Long robertoId;
    /** Uma segunda-feira daqui a uns 45 dias, sem feriado nela nem nos 2 dias seguintes. */
    protected LocalDate segunda;

    @BeforeEach
    void criarBia() throws Exception {
        LocalDate hoje = LocalDate.now(RelogioConfig.FUSO);
        Long engenharia = jdbc.queryForObject("SELECT id FROM departamento WHERE nome = 'Engenharia' AND empresa_id = ?",
                Long.class, empresaDemo);
        robertoId = idDoColaborador("roberto.neves@demo.crona");

        var json = mockMvc.perform(post("/colaboradores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "Bia Teste", "email": "bia.teste@demo.crona", "senha": "segredo123",
                         "perfil": "COLABORADOR", "dataAdmissao": "%s", "departamentoId": %d, "gestorId": %d}
                        """.formatted(hoje.minusMonths(18), engenharia, robertoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        biaId = ((Number) JsonPath.read(json, "$.id")).longValue();

        segunda = segundaLivreDepoisDe(hoje.plusDays(45));
    }

    protected Long idDoColaborador(String email) {
        return jdbc.queryForObject("SELECT id FROM colaborador WHERE email = ?", Long.class, email);
    }

    /** A primeira segunda-feira a partir de {@code data} sem feriado nela nem nos 2 dias seguintes. */
    protected LocalDate segundaLivreDepoisDe(LocalDate data) {
        LocalDate dia = data.with(TemporalAdjusters.nextOrSame(DayOfWeek.MONDAY));
        while (!feriadoRepository.datasEntre(empresaDemo, dia, dia.plusDays(2)).isEmpty()) {
            dia = dia.plusWeeks(1);
        }
        return dia;
    }
}
