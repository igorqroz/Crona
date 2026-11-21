package br.com.crona.ferias;

import java.time.LocalDate;
import java.util.List;

/**
 * O saldo de um período aquisitivo: o "30 dias · 15 em pedido · 15 livres" da tela.
 *
 * O saldo NÃO é guardado no banco: é sempre calculado a partir do direito e dos pedidos
 * (ver modelo-de-dominio.md, 3.5). Dado guardado em dois lugares acaba ficando diferente.
 *
 * @param direito   dias de direito do período (30, ou menos por causa de faltas)
 * @param aprovados dias de pedidos já aprovados
 * @param emPedido  dias de pedidos esperando o gestor ou o RH
 * @param livres    o que ainda dá para pedir (nunca negativo)
 */
public record SaldoDoPeriodo(int direito, int aprovados, int emPedido, int livres, Situacao situacao) {

    /** Faltando até estes dias para o limite, o período aparece como "a vencer". */
    static final int DIAS_PARA_ALERTA_DE_VENCIMENTO = 60;

    public enum Situacao {
        EM_AQUISICAO,   // ainda não completou os 12 meses
        DISPONIVEL,     // pode pedir
        A_VENCER,       // pode pedir, e o prazo está acabando
        VENCIDO,        // passou do prazo com dias sobrando (CLT-03: pagamento em dobro)
        ESGOTADO        // todos os dias já foram usados ou pedidos
    }

    /** Um pedido que usa dias do período: dias de descanso + dias vendidos (abono). */
    public record Consumo(int dias, StatusSolicitacao status) {
    }

    public static SaldoDoPeriodo calcular(LocalDate fim, LocalDate limiteConcessao, int direito,
                                          List<Consumo> consumos, LocalDate hoje) {
        int aprovados = somar(consumos, true);
        int emPedido = somar(consumos, false);
        int livres = Math.max(0, direito - aprovados - emPedido);

        return new SaldoDoPeriodo(direito, aprovados, emPedido, livres,
                situacao(fim, limiteConcessao, livres, hoje));
    }

    private static int somar(List<Consumo> consumos, boolean aprovados) {
        return consumos.stream()
                .filter(c -> c.status().consomeSaldo())
                .filter(c -> (c.status() == StatusSolicitacao.APROVADA) == aprovados)
                .mapToInt(Consumo::dias)
                .sum();
    }

    // A ORDEM dos "if" importa: cada um só é testado se os de cima não valeram.
    private static Situacao situacao(LocalDate fim, LocalDate limiteConcessao, int livres, LocalDate hoje) {
        if (!hoje.isAfter(fim)) {
            return Situacao.EM_AQUISICAO;
        }
        if (livres == 0) {
            return Situacao.ESGOTADO;
        }
        if (hoje.isAfter(limiteConcessao)) {
            return Situacao.VENCIDO;
        }
        if (!hoje.isBefore(limiteConcessao.minusDays(DIAS_PARA_ALERTA_DE_VENCIMENTO))) {
            return Situacao.A_VENCER;
        }
        return Situacao.DISPONIVEL;
    }
}
