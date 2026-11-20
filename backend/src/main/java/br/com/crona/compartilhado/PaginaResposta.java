package br.com.crona.compartilhado;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Uma página de resultados, num formato estável para o front-end.
 *
 * Por que não devolver o Page do Spring direto? Porque o JSON dele traz detalhes
 * internos e pode mudar entre versões do Spring. Este formato é nosso.
 *
 * {@code <T>} = "genérico": serve para página de qualquer coisa (colaboradores, solicitações...).
 */
public record PaginaResposta<T>(List<T> itens, int pagina, int tamanho, long total, int totalPaginas) {

    public static <E, T> PaginaResposta<T> de(Page<E> pagina, Function<E, T> conversor) {
        return new PaginaResposta<>(
                pagina.getContent().stream().map(conversor).toList(),
                pagina.getNumber(),
                pagina.getSize(),
                pagina.getTotalElements(),
                pagina.getTotalPages());
    }
}
