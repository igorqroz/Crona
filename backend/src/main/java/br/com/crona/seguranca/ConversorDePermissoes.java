package br.com.crona.seguranca;

import br.com.crona.colaborador.Perfil;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Lê o perfil de dentro do token e transforma nas permissões ("authorities") que o
 * Spring Security confere no @PreAuthorize.
 *
 * Repare: o token guarda só o PERFIL, e as permissões são calculadas a cada requisição.
 * Se o mapa em Permissao mudar, vale na hora, sem ninguém precisar fazer login de novo.
 */
public class ConversorDePermissoes implements Converter<Jwt, Collection<GrantedAuthority>> {

    @Override
    public Collection<GrantedAuthority> convert(Jwt token) {
        String perfilNoToken = token.getClaimAsString(TokenService.CLAIM_PERFIL);
        if (perfilNoToken == null) {
            return List.of();       // token sem perfil = nenhuma permissão
        }

        Perfil perfil;
        try {
            perfil = Perfil.valueOf(perfilNoToken);
        } catch (IllegalArgumentException perfilDesconhecido) {
            return List.of();
        }

        Collection<GrantedAuthority> permissoes = new ArrayList<>();
        Permissao.doPerfil(perfil).forEach(p -> permissoes.add(new SimpleGrantedAuthority(p.name())));
        return permissoes;
    }
}
