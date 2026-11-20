package br.com.crona.colaborador;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * O que a API recebe para CADASTRAR um colaborador.
 * A senha só aparece aqui: ela entra, vira hash, e nunca mais sai da API.
 */
public record ColaboradorCadastro(

        @NotBlank(message = "Informe o nome.")
        @Size(max = 150, message = "O nome pode ter no máximo 150 caracteres.")
        String nome,

        @NotBlank(message = "Informe o e-mail.")
        @Email(message = "E-mail inválido.")
        @Size(max = 254, message = "O e-mail pode ter no máximo 254 caracteres.")
        String email,

        // BCrypt só considera os primeiros 72 bytes da senha, por isso o máximo.
        @NotBlank(message = "Informe a senha inicial.")
        @Size(min = 8, max = 72, message = "A senha deve ter entre 8 e 72 caracteres.")
        String senha,

        @NotNull(message = "Informe o perfil.")
        Perfil perfil,

        @Size(max = 100, message = "O cargo pode ter no máximo 100 caracteres.")
        String cargo,

        @NotNull(message = "Informe a data de admissão.")
        LocalDate dataAdmissao,

        Long departamentoId,

        Long gestorId
) {

    /**
     * "Construtor compacto" do record: roda ANTES da validação do @Valid.
     * Tira os espaços das pontas, para " ana@x.com " não ser recusado como e-mail inválido.
     */
    public ColaboradorCadastro {
        nome = nome == null ? null : nome.trim();
        email = email == null ? null : email.trim();
    }
}
