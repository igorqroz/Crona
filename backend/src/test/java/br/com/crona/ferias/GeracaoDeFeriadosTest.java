package br.com.crona.ferias;

import br.com.crona.ApiTestBase;
import br.com.crona.compartilhado.RelogioConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * BUG-08: a rotina que mantém os feriados nacionais cadastrados à frente.
 *
 * O relógio é "parado" numa data distante (2040) para ver o horizonte andando sozinho.
 * Tudo é desfeito no fim do teste (@Transactional da ApiTestBase).
 */
class GeracaoDeFeriadosTest extends ApiTestBase {

    @Autowired
    private FeriadoRepository feriadoRepository;
    @Autowired
    private JdbcTemplate jdbc;

    private static Clock em(int ano) {
        return Clock.fixed(LocalDate.of(ano, 6, 15).atStartOfDay(RelogioConfig.FUSO).toInstant(), RelogioConfig.FUSO);
    }

    private int nacionaisDe(int ano) {
        return jdbc.queryForObject("""
                SELECT count(*) FROM feriado WHERE empresa_id IS NULL AND extract(year FROM data) = ?
                """, Integer.class, ano);
    }

    @Test
    @DisplayName("em 2040, cadastra do ano atual até 10 anos à frente, 10 feriados por ano")
    void horizonteAnda() {
        new GeracaoDeFeriados(feriadoRepository, em(2040)).todoDia();

        assertThat(nacionaisDe(2040)).isEqualTo(10);
        assertThat(nacionaisDe(2050)).isEqualTo(10);
        assertThat(nacionaisDe(2051)).isZero();
    }

    @Test
    @DisplayName("rodar de novo (todo dia, ou a cada partida) não duplica nada")
    void naoDuplica() {
        var rotina = new GeracaoDeFeriados(feriadoRepository, em(2040));
        rotina.todoDia();
        Integer depoisDaPrimeira = jdbc.queryForObject("SELECT count(*) FROM feriado", Integer.class);

        rotina.todoDia();
        rotina.aoIniciar();

        assertThat(jdbc.queryForObject("SELECT count(*) FROM feriado", Integer.class)).isEqualTo(depoisDaPrimeira);
    }

    @Test
    @DisplayName("não mexe nos feriados que já existem: os de 2026 da V2 continuam, e o feriado da empresa também")
    void naoMexeNoQueJaExiste() {
        jdbc.update("INSERT INTO feriado (empresa_id, data, descricao) VALUES (?, '2026-02-17', 'Carnaval da empresa')",
                empresaDemo);
        var antes = jdbc.queryForList("SELECT id FROM feriado WHERE extract(year FROM data) = 2026 ORDER BY id", Long.class);

        new GeracaoDeFeriados(feriadoRepository, em(2026)).todoDia();

        var depois = jdbc.queryForList("SELECT id FROM feriado WHERE extract(year FROM data) = 2026 ORDER BY id", Long.class);
        assertThat(depois).containsExactlyElementsOf(antes);
    }

    @Test
    @DisplayName("os nomes e datas calculados de 2026 e 2027 são os mesmos gravados pela V2")
    void igualAoQueAV2Gravou() {
        for (int ano : new int[] {2026, 2027}) {
            List<String> calculados = FeriadosNacionais.doAno(ano).stream()
                    .map(f -> f.data() + " " + f.descricao())
                    .toList();
            List<String> gravados = jdbc.queryForList("""
                    SELECT data || ' ' || descricao FROM feriado
                    WHERE empresa_id IS NULL AND extract(year FROM data) = ?
                    """, String.class, ano);

            assertThat(gravados).containsExactlyInAnyOrderElementsOf(calculados);
        }
    }

    @Test
    @DisplayName("se o banco falhar, a rotina não derruba a API (só registra o erro)")
    void falhaNaoDerruba() {
        var quebrado = mock(FeriadoRepository.class);
        when(quebrado.inserirNacional(any(), any())).thenThrow(new IllegalStateException("banco fora"));
        var rotina = new GeracaoDeFeriados(quebrado, em(2040));

        assertThatCode(rotina::aoIniciar).doesNotThrowAnyException();
        assertThatCode(rotina::todoDia).doesNotThrowAnyException();
    }
}
