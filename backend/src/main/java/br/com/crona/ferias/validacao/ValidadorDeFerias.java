package br.com.crona.ferias.validacao;

import br.com.crona.ferias.SaldoDoPeriodo.Situacao;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static br.com.crona.ferias.validacao.Verificacao.atencao;
import static br.com.crona.ferias.validacao.Verificacao.impedimento;
import static br.com.crona.ferias.validacao.Verificacao.ok;

/**
 * Confere um pedido de férias contra as regras da CLT e devolve o checklist da tela
 * "Solicitar férias". Cada regra vira um método, e cada método devolve UMA verificação
 * (passou, ponto de atenção ou impedimento), com o código da regra em regras-de-negocio.md.
 *
 * Classe de domínio pura: recebe os dados prontos e não acessa banco.
 */
public final class ValidadorDeFerias {

    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int MINIMO_POR_PARTE = 5;
    private static final int MINIMO_PARTE_PRINCIPAL = 14;
    private static final int MAXIMO_DE_PARTES = 3;
    private static final int ANTECEDENCIA_DA_CLT = 30;
    private static final int PRAZO_DO_ABONO_EM_DIAS = 15;

    private ValidadorDeFerias() {
    }

    public static ResultadoDaValidacao validar(DadosParaValidar dados) {
        var pedido = dados.pedido();
        LocalDate fim = pedido.inicio().plusDays(Math.max(pedido.diasGozo(), 1) - 1L);

        List<Verificacao> verificacoes = new ArrayList<>();
        verificacoes.add(outrasFerias(dados, fim));
        verificacoes.add(periodoCompleto(dados));
        verificacoes.add(prazoDeConcessao(dados, fim));
        verificacoes.add(saldo(dados));
        verificacoes.add(divisao(dados));
        verificacoes.add(diaDeInicio(dados));
        if (pedido.diasAbono() > 0) {
            verificacoes.add(abono(dados));
        }
        verificacoes.add(antecedencia(dados));

        return new ResultadoDaValidacao(pedido.inicio(), fim, proximoDiaUtil(fim.plusDays(1), dados.feriados()),
                List.copyOf(verificacoes));
    }

    // ------------------------------------------------------------------ CRONA-01

    /** Ninguém tira férias duas vezes no mesmo dia: as datas não podem coincidir com outras férias suas. */
    private static Verificacao outrasFerias(DadosParaValidar dados, LocalDate fim) {
        LocalDate inicio = dados.pedido().inicio();
        var coincide = dados.outrasFerias().stream()
                .filter(f -> !f.inicio().isAfter(fim) && !f.fim().isBefore(inicio))
                .findFirst();
        if (coincide.isPresent()) {
            var f = coincide.get();
            return impedimento("CRONA-01", "Você já tem férias " + (f.aprovada() ? "aprovadas" : "em análise")
                    + " de " + f.inicio().format(DATA) + " a " + f.fim().format(DATA)
                    + ", que coincidem com estas datas.");
        }
        return ok("CRONA-01", "Não coincide com outras férias suas");
    }

    // ------------------------------------------------------------------ CLT-01

    private static Verificacao periodoCompleto(DadosParaValidar dados) {
        var periodo = dados.periodo();
        if (periodo.situacao() == Situacao.EM_AQUISICAO) {
            return impedimento("CLT-01", "O período aquisitivo " + periodo.numero()
                    + " ainda não completou 12 meses. Ele só pode ser usado a partir de "
                    + periodo.fim().plusDays(1).format(DATA) + ".");
        }
        return ok("CLT-01", "Período aquisitivo " + periodo.numero() + " completo");
    }

    // ------------------------------------------------------------------ CLT-03

    private static Verificacao prazoDeConcessao(DadosParaValidar dados, LocalDate fim) {
        LocalDate limite = dados.periodo().limiteConcessao();
        // Período que JÁ venceu: as férias continuam devidas (a lei manda conceder, pagando em dobro).
        // Bloquear aqui deixaria esses dias presos para sempre; o certo é avisar e incentivar a tirar logo.
        if (dados.periodo().situacao() == Situacao.VENCIDO) {
            return atencao("CLT-03", "Este período venceu em " + limite.format(DATA)
                    + ": as férias serão pagas em dobro. O ideal é tirar o quanto antes.");
        }
        if (fim.isAfter(limite)) {
            return impedimento("CLT-03", "As férias iriam até " + fim.format(DATA)
                    + ", depois do prazo deste período (" + limite.format(DATA)
                    + "). Fora do prazo, a empresa paga em dobro.");
        }
        return ok("CLT-03", "Dentro do prazo de concessão (até " + limite.format(DATA) + ")");
    }

    // ------------------------------------------------------------------ saldo

    private static Verificacao saldo(DadosParaValidar dados) {
        int pedidos = dados.pedido().diasGozo() + dados.pedido().diasAbono();
        int livres = dados.periodo().livres();
        if (pedidos > livres) {
            return impedimento("SALDO", "Passa do saldo em " + (pedidos - livres)
                    + " dias (você tem " + livres + " livres neste período)");
        }
        return ok("SALDO", "Cabe no seu saldo (sobram " + (livres - pedidos) + " dias)");
    }

    // ------------------------------------------------------------------ CLT-04

    private static Verificacao divisao(DadosParaValidar dados) {
        int dias = dados.pedido().diasGozo();
        List<Integer> anteriores = dados.partesJaPedidas();

        if (dias < MINIMO_POR_PARTE) {
            return impedimento("CLT-04", "Cada parte das férias precisa ter pelo menos " + MINIMO_POR_PARTE + " dias.");
        }
        int partes = anteriores.size() + 1;
        if (partes > MAXIMO_DE_PARTES) {
            return impedimento("CLT-04", "Este período já foi dividido em " + MAXIMO_DE_PARTES
                    + " partes, o máximo permitido pela CLT.");
        }

        int sobra = Math.max(0, dados.periodo().livres() - dias - dados.pedido().diasAbono());
        boolean temParteDe14 = dias >= MINIMO_PARTE_PRINCIPAL
                || anteriores.stream().anyMatch(p -> p >= MINIMO_PARTE_PRINCIPAL);

        if (!temParteDe14) {
            // Ainda dá para cumprir a regra depois? Precisa sobrar uma "vaga" de parte e 14 dias.
            if (partes < MAXIMO_DE_PARTES && sobra >= MINIMO_PARTE_PRINCIPAL) {
                return ok("CLT-04", dias + " dias: se dividir, outra parte precisa ter "
                        + MINIMO_PARTE_PRINCIPAL + " dias ou mais");
            }
            return impedimento("CLT-04", "Com este pedido, nenhuma parte das férias teria "
                    + MINIMO_PARTE_PRINCIPAL + " dias ou mais, e a CLT exige uma.");
        }

        if (sobra > 0 && sobra < MINIMO_POR_PARTE) {
            return atencao("CLT-04", "Sobrariam " + sobra + " dias, que não formam uma parte (mínimo "
                    + MINIMO_POR_PARTE + "). O gestor verá isso.");
        }
        return ok("CLT-04", dias >= MINIMO_PARTE_PRINCIPAL
                ? dias + " dias: vale como período principal (" + MINIMO_PARTE_PRINCIPAL + "+)"
                : dias + " dias: o período principal de " + MINIMO_PARTE_PRINCIPAL + "+ dias já foi pedido");
    }

    // ------------------------------------------------------------------ CLT-05

    private static Verificacao diaDeInicio(DadosParaValidar dados) {
        LocalDate inicio = dados.pedido().inicio();
        Set<LocalDate> feriados = dados.feriados();

        if (feriados.contains(inicio)) {
            return impedimento("CLT-05", "Começa num feriado. Escolha um dia útil.");
        }
        if (fimDeSemana(inicio)) {
            return impedimento("CLT-05", "Começa num " + nomeDoDia(inicio) + ", que é dia de folga. Escolha um dia útil.");
        }
        for (int i = 1; i <= 2; i++) {
            LocalDate dia = inicio.plusDays(i);
            if (feriados.contains(dia)) {
                return impedimento("CLT-05", "Começa a " + i + " dia(s) de um feriado (" + dia.format(DATA)
                        + "). A CLT não permite início nos 2 dias antes de feriado ou folga.");
            }
            if (fimDeSemana(dia)) {
                return impedimento("CLT-05", "Começa numa " + nomeDoDia(inicio)
                        + ". A CLT não permite início nos 2 dias antes da folga: escolha de segunda a quarta.");
            }
        }
        return ok("CLT-05", "Começa numa " + nomeDoDia(inicio) + ": não antecede folga");
    }

    // ------------------------------------------------------------------ CLT-06

    private static Verificacao abono(DadosParaValidar dados) {
        int maximo = dados.periodo().direito() / 3;
        int total = dados.abonoJaPedido() + dados.pedido().diasAbono();
        if (total > maximo) {
            return impedimento("CLT-06", "O abono pode ter no máximo " + maximo + " dias (1/3 de "
                    + dados.periodo().direito() + ")" + (dados.abonoJaPedido() > 0
                    ? ", e " + dados.abonoJaPedido() + " já foram pedidos neste período." : "."));
        }
        LocalDate prazo = dados.periodo().fim().minusDays(PRAZO_DO_ABONO_EM_DIAS);
        if (dados.hoje().isAfter(prazo)) {
            return atencao("CLT-06", "Vender " + dados.pedido().diasAbono() + " dias depois do prazo legal ("
                    + prazo.format(DATA) + ") depende de acordo com a empresa. O gestor verá isso.");
        }
        return ok("CLT-06", "Abono de " + dados.pedido().diasAbono() + " dias dentro do limite de 1/3");
    }

    // ------------------------------------------------------------------ CLT-07 / EMP-03

    private static Verificacao antecedencia(DadosParaValidar dados) {
        long dias = ChronoUnit.DAYS.between(dados.hoje(), dados.pedido().inicio());
        int minimo = Math.max(ANTECEDENCIA_DA_CLT, dados.antecedenciaMinima());
        if (dias <= 0) {
            return impedimento("CLT-07", "A data de início precisa ser depois de hoje.");
        }
        if (dias < minimo) {
            return atencao("CLT-07", "Faltam só " + dias + " dias para o início; o mínimo é " + minimo
                    + " dias de antecedência. O gestor verá isso.");
        }
        return ok("CLT-07", dias + " dias de antecedência (mínimo " + minimo + ")");
    }

    // ------------------------------------------------------------------ auxiliares

    private static boolean fimDeSemana(LocalDate dia) {
        return dia.getDayOfWeek() == DayOfWeek.SATURDAY || dia.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    private static LocalDate proximoDiaUtil(LocalDate dia, Set<LocalDate> feriados) {
        while (fimDeSemana(dia) || feriados.contains(dia)) {
            dia = dia.plusDays(1);
        }
        return dia;
    }

    private static String nomeDoDia(LocalDate dia) {
        return switch (dia.getDayOfWeek()) {
            case MONDAY -> "segunda";
            case TUESDAY -> "terça";
            case WEDNESDAY -> "quarta";
            case THURSDAY -> "quinta";
            case FRIDAY -> "sexta";
            case SATURDAY -> "sábado";
            case SUNDAY -> "domingo";
        };
    }
}
