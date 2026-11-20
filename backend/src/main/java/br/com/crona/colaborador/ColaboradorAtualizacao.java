package br.com.crona.colaborador;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * O que a API recebe para ALTERAR um colaborador. Igual ao cadastro, mas sem senha:
 * trocar senha vai ser uma ação própria, com o login (Fase 3).
 */
public record ColaboradorAtualizacao(

        @NotBlank(message = "Informe o nome.")
        @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres.")
        String nome,

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "E-mail inválido.")
        @Size(max = 254, message = "O e-mail pode ter no máximo 254 caracteres.")
        String email,

        @NotNull(message = "Informe o perfil.")
        Perfil perfil,

        @Size(max = 100, message = "O cargo pode ter no máximo 100 caracteres.")
        String cargo,

        @NotNull(message = "Informe a data de admissão.")
        LocalDate dataAdmissao,

        Long departamentoId,

        Long gestorId
) {

    /** Tira os espaços das pontas antes da validação (ver ColaboradorCadastro). */
    public ColaboradorAtualizacao {
        nome = nome == null ? null : nome.trim();
        email = email == null ? null : email.trim();
    }
}
