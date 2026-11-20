package br.com.crona.seguranca;

import br.com.crona.empresa.EmpresaAtual;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;

/**
 * A empresa de quem está usando o sistema, lida do token da requisição.
 *
 * Substitui a "Empresa Demo fixa" da Fase 2. Os services continuam pedindo a empresa para
 * a interface EmpresaAtual, então nenhum deles precisou mudar.
 *
 * O token é assinado pelo servidor: o usuário não consegue trocar a empresa dele por outra
 * sem invalidar a assinatura (armadilha A-02 resolvida).
 */
@Component
class EmpresaAtualDoToken implements EmpresaAtual {

    @Override
    public Long id() {
        // O Spring Security guarda, para cada requisição, quem está autenticado.
        var autenticacao = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacao == null || !(autenticacao.getPrincipal() instanceof Jwt token)) {
            throw new IllegalStateException("Requisição sem usuário autenticado.");
        }
        Number empresaId = token.getClaim(TokenService.CLAIM_EMPRESA);
        if (empresaId == null) {
            throw new IllegalStateException("Token sem a empresa (" + TokenService.CLAIM_EMPRESA + ").");
        }
        return empresaId.longValue();
    }
}
