package br.com.crona.compartilhado.erro;

/** Vira HTTP 401: e-mail ou senha não conferem. */
public class CredenciaisInvalidasException extends RuntimeException {

    public CredenciaisInvalidasException() {
        // A mensagem é a mesma para "e-mail não existe" e "senha errada", de propósito:
        // dizer qual dos dois errou ajudaria alguém a descobrir quais e-mails têm conta.
        super("E-mail ou senha inválidos.");
    }
}
