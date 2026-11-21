package br.com.crona.compartilhado.erro;

import java.util.List;

/**
 * Vira HTTP 422 com a lista completa de verificações no corpo, para a tela mostrar
 * exatamente o que precisa ser ajustado (e não só "pedido inválido").
 */
public class PedidoComImpedimentosException extends RuntimeException {

    private final transient List<?> verificacoes;

    public PedidoComImpedimentosException(String mensagem, List<?> verificacoes) {
        super(mensagem);
        this.verificacoes = List.copyOf(verificacoes);
    }

    public List<?> getVerificacoes() {
        return verificacoes;
    }
}
