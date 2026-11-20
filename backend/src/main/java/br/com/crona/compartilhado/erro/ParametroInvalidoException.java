package br.com.crona.compartilhado.erro;

/** Vira HTTP 400: um parâmetro da URL não é aceito (ex.: ?sort=campoQueNaoExiste). */
public class ParametroInvalidoException extends RuntimeException {

    public ParametroInvalidoException(String mensagem) {
        super(mensagem);
    }
}
