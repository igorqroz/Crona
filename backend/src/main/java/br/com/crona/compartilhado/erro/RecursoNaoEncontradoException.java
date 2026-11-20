package br.com.crona.compartilhado.erro;

/** Vira HTTP 404: o que foi pedido não existe (ou é de outra empresa). */
public class RecursoNaoEncontradoException extends RuntimeException {

    public RecursoNaoEncontradoException(String mensagem) {
        super(mensagem);
    }
}
