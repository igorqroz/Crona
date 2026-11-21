package br.com.crona.ferias.validacao;

import br.com.crona.ferias.validacao.DadosDaEquipe.Ausencia;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import static br.com.crona.ferias.validacao.Verificacao.atencao;
import static br.com.crona.ferias.validacao.Verificacao.impedimento;
import static br.com.crona.ferias.validacao.Verificacao.ok;

/**
 * Regras que dependem dos colegas e da empresa: EMP-01, EMP-02, EMP-04 e EMP-05.
 * Classe de domínio pura, como o ValidadorDeFerias.
 */
public final class ValidadorDaEquipe {

    private static final DateTimeFormatter DIA_MES = DateTimeFormatter.ofPattern("dd/MM");

    private ValidadorDaEquipe() {
    }

    /** @param colegasNoPeriodo EMP-02: quem do departamento estará fora (informativo, não é regra) */
    public record Resultado(List<Verificacao> verificacoes, List<Ausencia> colegasNoPeriodo) {
    }

    public static Resultado validar(DadosDaEquipe dados) {
        List<Verificacao> verificacoes = new ArrayList<>();
        var departamento = dados.departamento();

        // EMP-01
        if (departamento != null && departamento.percentualMinimoTrabalhando() != null) {
            int limite = limiteDoDepartamento(departamento);
            var pico = pico(dados.inicio(), dados.fim(), dados.ausenciasDoDepartamento());
            verificacoes.add(pico.pessoas() > limite
                    ? atencao("EMP-01", pico.pessoas() + " pessoas fora " + pico.quando() + ", acima do limite de "
                            + limite + " do departamento " + departamento.nome() + ". O gestor verá isso.")
                    : ok("EMP-01", departamento.nome() + " com no máximo " + pico.pessoas() + " de "
                            + departamento.pessoas() + " pessoas fora (limite " + limite + ")"));
        }

        // EMP-04
        if (dados.limiteDaEmpresa() != null) {
            var pico = pico(dados.inicio(), dados.fim(), dados.ausenciasDaEmpresa());
            verificacoes.add(pico.pessoas() > dados.limiteDaEmpresa()
                    ? atencao("EMP-04", pico.pessoas() + " pessoas da empresa fora " + pico.quando()
                            + ", acima do limite de " + dados.limiteDaEmpresa() + ". O gestor verá isso.")
                    : ok("EMP-04", "Empresa dentro do limite de " + dados.limiteDaEmpresa() + " pessoas fora"));
        }

        // EMP-05
        for (var restricao : dados.restricoes()) {
            if (encosta(dados.inicio(), dados.fim(), restricao.inicio(), restricao.fim())) {
                String quando = restricao.motivo() + " (" + restricao.inicio().format(DIA_MES) + " a "
                        + restricao.fim().format(DIA_MES) + ")";
                verificacoes.add(restricao.bloqueia()
                        ? impedimento("EMP-05", "Período restrito: " + quando + ". Não é possível tirar férias nessas datas.")
                        : atencao("EMP-05", "Período sensível: " + quando + ". O gestor verá isso."));
            }
        }

        // EMP-02 (informativo)
        var colegas = dados.ausenciasDoDepartamento().stream()
                .filter(a -> encosta(dados.inicio(), dados.fim(), a.inicio(), a.fim()))
                .toList();

        return new Resultado(List.copyOf(verificacoes), colegas);
    }

    /**
     * EMP-01: com 10 pessoas e 70% trabalhando, no máximo 3 ficam fora (10 x 30% = 3).
     * Nunca menos que 1: num departamento de 2 pessoas, 2 x 30% = 0,6, e arredondar para 0
     * significaria que ninguém nunca tira férias.
     */
    public static int limiteDoDepartamento(DadosDaEquipe.Departamento departamento) {
        int podeFicarFora = departamento.pessoas() * (100 - departamento.percentualMinimoTrabalhando()) / 100;
        return Math.max(1, podeFicarFora);
    }

    /** O dia (ou a sequência de dias) com mais gente fora, contando quem está pedindo. */
    private static Pico pico(LocalDate inicio, LocalDate fim, List<Ausencia> outros) {
        int maior = 0;
        LocalDate de = inicio;
        LocalDate ate = inicio;
        for (LocalDate dia = inicio; !dia.isAfter(fim); dia = dia.plusDays(1)) {
            final LocalDate d = dia;
            int fora = 1 + (int) outros.stream().filter(a -> !d.isBefore(a.inicio()) && !d.isAfter(a.fim())).count();
            if (fora > maior) {
                maior = fora;
                de = dia;
                ate = dia;
            } else if (fora == maior && ate.equals(dia.minusDays(1))) {
                ate = dia;      // o pico continua no dia seguinte: estica a sequência
            }
        }
        return new Pico(maior, de, ate);
    }

    private record Pico(int pessoas, LocalDate de, LocalDate ate) {
        String quando() {
            return de.equals(ate) ? "em " + de.format(DIA_MES) : "de " + de.format(DIA_MES) + " a " + ate.format(DIA_MES);
        }
    }

    /** Dois períodos se encostam se um começa antes de o outro terminar (e vice-versa). */
    private static boolean encosta(LocalDate inicioA, LocalDate fimA, LocalDate inicioB, LocalDate fimB) {
        return !inicioA.isAfter(fimB) && !inicioB.isAfter(fimA);
    }
}
