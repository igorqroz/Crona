package br.com.crona.seguranca;

import br.com.crona.colaborador.Perfil;

import java.time.Instant;

/**
 * O que o login devolve: o token e o mínimo que a tela precisa para mostrar quem entrou.
 * (Armadilha A-03: o front guarda só isto, e não o cadastro inteiro do usuário.)
 */
public record LoginResposta(String token, String tipo, Instant expiraEm, Usuario usuario) {

    public record Usuario(Long id, String nome, Perfil perfil) {
    }
}
