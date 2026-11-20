package br.com.crona.compartilhado.erro;

/** Vira HTTP 409: o pedido é válido, mas bate com o estado atual (ex.: nome repetido). */
public class ConflitoException extends RuntimeException {

    public ConflitoException(String mensagem) {
        super(mensagem);
    }
}
