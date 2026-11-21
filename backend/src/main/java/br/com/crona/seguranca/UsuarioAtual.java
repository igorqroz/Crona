package br.com.crona.seguranca;

import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * Quem está usando o sistema agora: o id do colaborador, lido do token ("sub").
 * Usado nas rotas "minhas" (/minhas-ferias/...), em que cada um só vê o que é seu.
 */
@Component
public class UsuarioAtual {

    public Long id() {
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof Jwt token)) {
            throw new IllegalStateException("Requisição sem usuário autenticado.");
        }
        return Long.valueOf(token.getSubject());
    }
}
