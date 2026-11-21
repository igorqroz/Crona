package br.com.crona.ferias.validacao;

/**
 * Uma linha do checklist da tela "Solicitar férias":
 *   ✅ "Começa numa segunda: não antecede folga"
 *   ⚠️ "Menos de 30 dias de antecedência: o gestor verá isso"
 *   ❌ "Passa do saldo em 5 dias"
 *
 * @param regra o código da regra em regras-de-negocio.md (ex.: "CLT-05"), para rastrear de onde veio
 */
public record Verificacao(String regra, Resultado resultado, String mensagem) {

    public enum Resultado {
        OK,
        ATENCAO,        // pode enviar; o gestor vê o aviso
        IMPEDIMENTO     // não pode enviar
    }

    static Verificacao ok(String regra, String mensagem) {
        return new Verificacao(regra, Resultado.OK, mensagem);
    }

    static Verificacao atencao(String regra, String mensagem) {
        return new Verificacao(regra, Resultado.ATENCAO, mensagem);
    }

    static Verificacao impedimento(String regra, String mensagem) {
        return new Verificacao(regra, Resultado.IMPEDIMENTO, mensagem);
    }
}
