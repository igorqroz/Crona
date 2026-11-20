package br.com.crona.departamento;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * O que a API RECEBE para criar ou renomear um departamento.
 *
 * {@code record}: uma classe só de dados, imutável. O Java gera sozinho o construtor,
 * os "getters" (nome()), equals, hashCode e toString.
 *
 * Por que não receber a entidade Departamento direto? Porque aí o cliente poderia
 * mandar campos que não deveria, como id ou empresa.
 */
public record DepartamentoRequisicao(

        @NotBlank(message = "Informe o nome do departamento.")
        @Size(max = 100, message = "O nome pode ter no máximo 100 caracteres.")
        String nome
) {
}
