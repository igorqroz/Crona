package br.com.crona.ferias;

import java.time.LocalDate;
import java.util.List;

/**
 * Os feriados nacionais de um ano, calculados (Leis 662/1949, 6.802/1980 e 14.759/2023).
 * Nove têm data fixa; a Sexta-feira Santa muda porque depende da Páscoa.
 * Carnaval e Corpus Christi são ponto facultativo, não feriado nacional, então ficam de fora.
 */
public final class FeriadosNacionais {

    public record Nacional(LocalDate data, String descricao) {
    }

    private FeriadosNacionais() {
    }

    /** Os mesmos nomes e datas da migração V2 (2026 e 2027), para qualquer ano. */
    public static List<Nacional> doAno(int ano) {
        return List.of(
                new Nacional(LocalDate.of(ano, 1, 1), "Confraternização Universal"),
                new Nacional(pascoa(ano).minusDays(2), "Sexta-feira Santa"),
                new Nacional(LocalDate.of(ano, 4, 21), "Tiradentes"),
                new Nacional(LocalDate.of(ano, 5, 1), "Dia do Trabalho"),
                new Nacional(LocalDate.of(ano, 9, 7), "Independência do Brasil"),
                new Nacional(LocalDate.of(ano, 10, 12), "Nossa Senhora Aparecida"),
                new Nacional(LocalDate.of(ano, 11, 2), "Finados"),
                new Nacional(LocalDate.of(ano, 11, 15), "Proclamação da República"),
                new Nacional(LocalDate.of(ano, 11, 20), "Dia Nacional de Zumbi e da Consciência Negra"),
                new Nacional(LocalDate.of(ano, 12, 25), "Natal"));
    }

    /** Domingo de Páscoa no calendário gregoriano (algoritmo de Meeus/Jones/Butcher). */
    static LocalDate pascoa(int ano) {
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
        return LocalDate.of(ano, mes, dia);
    }
}
