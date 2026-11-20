package br.com.crona.compartilhado.erro;

/**
 * Vira HTTP 422 (Unprocessable Content): o JSON está certo, mas o pedido quebra uma
 * regra do negócio. Ex.: escolher como gestor alguém com perfil COLABORADOR.
 *
 * Diferença para o 400: o 400 é "não entendi o que você mandou" (campo vazio, JSON quebrado).
 * O 422 é "entendi, mas não posso fazer isso".
 */
public class RegraVioladaException extends RuntimeException {

    public RegraVioladaException(String mensagem) {
        super(mensagem);
    }
}
