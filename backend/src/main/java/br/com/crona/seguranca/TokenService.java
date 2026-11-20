package br.com.crona.seguranca;

import br.com.crona.colaborador.Colaborador;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;

/**
 * Fabrica o token (JWT) entregue no login.
 *
 * Um JWT tem 3 partes separadas por ponto:  cabeçalho . dados . assinatura
 * - Os DADOS ("claims") podem ser lidos por qualquer um (são só Base64, não criptografia).
 *   Por isso o token nunca leva senha nem dado sensível.
 * - A ASSINATURA é feita com o segredo do servidor. Se alguém mudar um dado (ex.: trocar
 *   o perfil para ADMIN), a assinatura deixa de bater e o token é recusado.
 */
@Service
public class TokenService {

    static final String CLAIM_EMPRESA = "empresa_id";
    static final String CLAIM_PERFIL = "perfil";
    static final String CLAIM_NOME = "nome";

    private final JwtEncoder jwtEncoder;
    private final JwtPropriedades propriedades;
    private final Clock relogio;

    // Com dois construtores, o Spring não adivinha qual usar: o @Autowired indica este.
    @Autowired
    public TokenService(JwtEncoder jwtEncoder, JwtPropriedades propriedades) {
        this(jwtEncoder, propriedades, Clock.systemUTC());
    }

    // O relógio entra pelo construtor para os testes poderem "congelar" a hora.
    TokenService(JwtEncoder jwtEncoder, JwtPropriedades propriedades, Clock relogio) {
        this.jwtEncoder = jwtEncoder;
        this.propriedades = propriedades;
        this.relogio = relogio;
    }

    public Jwt gerar(Colaborador colaborador) {
        Instant agora = Instant.now(relogio);
        var dados = JwtClaimsSet.builder()
                .issuer("crona")                                         // quem emitiu
                .subject(colaborador.getId().toString())                 // de quem é o token
                .issuedAt(agora)                                         // quando foi emitido
                .expiresAt(agora.plus(propriedades.validade()))          // quando vence
                .claim(CLAIM_EMPRESA, colaborador.getEmpresa().getId())
                .claim(CLAIM_PERFIL, colaborador.getPerfil().name())
                .claim(CLAIM_NOME, colaborador.getNome())
                .build();
        var cabecalho = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(cabecalho, dados));
    }
}
