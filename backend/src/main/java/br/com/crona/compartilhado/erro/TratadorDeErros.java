package br.com.crona.compartilhado.erro;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.core.PropertyReferenceException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

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
 *
 * Estende ResponseEntityExceptionHandler: a classe do Spring que já sabe responder, nesse
 * mesmo formato, os erros do próprio framework (rota inexistente 404, método errado 405...).
 * Aqui sobrescrevemos só os que queremos em português.
 */
@RestControllerAdvice
public class TratadorDeErros extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(TratadorDeErros.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ProblemDetail naoEncontrado(RecursoNaoEncontradoException e) {
        return problema(HttpStatus.NOT_FOUND, "Não encontrado", e.getMessage());
    }

    @ExceptionHandler(ConflitoException.class)
    public ProblemDetail conflito(ConflitoException e) {
        return problema(HttpStatus.CONFLICT, "Conflito", e.getMessage());
    }

    @ExceptionHandler(RegraVioladaException.class)
    public ProblemDetail regraViolada(RegraVioladaException e) {
        return problema(HttpStatus.UNPROCESSABLE_CONTENT, "Regra de negócio", e.getMessage());
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ProblemDetail parametroInvalido(ParametroInvalidoException e) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos", e.getMessage());
    }

    // ?sort=campoQueNaoExiste numa consulta gerada pelo nome do método.
    @ExceptionHandler(PropertyReferenceException.class)
    public ProblemDetail ordenacaoInvalida(PropertyReferenceException e) {
        return problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "Não é possível ordenar por \"" + e.getPropertyName() + "\".");
    }

    // Rede de segurança: o banco recusou por uma restrição (UNIQUE, FK, CHECK) que o
    // service não pegou antes. Ex.: duas pessoas criando o mesmo nome ao mesmo tempo.
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail violacaoNoBanco(DataIntegrityViolationException e) {
        return problema(HttpStatus.CONFLICT, "Conflito",
                "A operação não pôde ser concluída porque entra em conflito com dados existentes.");
    }

    // Último recurso: qualquer erro inesperado. O detalhe técnico vai para o LOG,
    // nunca para a resposta (ele pode revelar nomes de tabela, SQL, caminhos do servidor).
    @ExceptionHandler(Exception.class)
    public ProblemDetail erroInesperado(Exception e) {
        log.error("Erro inesperado", e);
        return problema(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno",
                "Ocorreu um erro inesperado. Tente de novo em instantes.");
    }

    // ------------------------------------------------------------ erros do próprio Spring

    // Erros do @Valid: devolve um campo "erros" com a mensagem de cada campo inválido.
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        Map<String, String> erros = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(erro -> erros.putIfAbsent(erro.getField(), erro.getDefaultMessage()));

        var problema = problema(HttpStatus.BAD_REQUEST, "Dados inválidos", "Confira os campos destacados.");
        problema.setProperty("erros", erros);
        return ResponseEntity.badRequest().body(problema);
    }

    // JSON malformado, corpo vazio ou valor que não existe (ex.: "perfil": "CHEFE").
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException e,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        return ResponseEntity.badRequest().body(problema(HttpStatus.BAD_REQUEST, "Dados inválidos",
                "O corpo da requisição não é um JSON válido ou tem valores não aceitos."));
    }

    private ProblemDetail problema(HttpStatus status, String titulo, String detalhe) {
        var problema = ProblemDetail.forStatusAndDetail(status, detalhe);
        problema.setTitle(titulo);
        return problema;
    }
}
