package br.com.crona.compartilhado.erro;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Transforma exceções em respostas HTTP claras, em português, num formato padrão.
 *
 * {@code @RestControllerAdvice}: vale para TODOS os controllers. Assim nenhum controller
 * precisa de try/catch.
 *
 * O formato é o ProblemDetail (RFC 9457), um padrão da web para erros de API:
 * { "status": 409, "title": "Conflito", "detail": "Já existe um departamento..." }
 */
@RestControllerAdvice
public class TratadorDeErros {

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException e) {
        return problema(HttpStatus.CONFLICT, "Conflito", e.getMessage());
    }

    // Erros do @Valid: devolve um campo "erros" com a mensagem de cada campo inválido.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail dadosInvalidos(MethodArgumentNotValidException e) {
        Map<String, String> erros = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> erros.putIfAbsent(erro.getField(), erro.getDefaultMessage()));

        var problema = problema(HttpStatus.BAD_REQUEST, "Dados inválidos", "Confira os campos destacados.");
        problema.setProperty("erros", erros);
        return problema;
    }

    // JSON malformado ou corpo vazio.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ProblemDetail corpoIlegivel(HttpMessageNotReadableException e) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos", "O corpo da requisição não é um JSON válido.");
    }

    // Rede de segurança: o banco recusou por uma restrição (UNIQUE, FK, CHECK) que o
    // service não pegou antes. Ex.: duas pessoas criando o mesmo nome ao mesmo tempo.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail violacaoNoBanco(DataIntegrityViolationException e) {
        return problema(HttpStatus.CONFLICT, "Conflito",
                "A operação não pôde ser concluída porque entra em conflito com dados existentes.");
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        var problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
