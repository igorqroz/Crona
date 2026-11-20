package br.com.crona.seguranca;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.servlet.HandlerExceptionResolver;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/**
 * Configuração de segurança da API.
 *
 * Como funciona a partir de agora:
 * 1. O cliente faz POST /auth/login com e-mail e senha e recebe um TOKEN (JWT).
 * 2. Em toda outra requisição, ele envia o cabeçalho  Authorization: Bearer <token>.
 * 3. Antes de chegar no controller, o Spring Security confere a assinatura e a validade do token.
 *    Sem token, ou com token inválido/vencido: 401.
 */
@Configuration
@EnableConfigurationProperties(JwtPropriedades.class)
// Liga o @PreAuthorize nos controllers: a permissão é conferida antes de o método rodar.
@EnableMethodSecurity
public class SegurancaConfig {

    @Bean
    public SecurityFilterChain filtrosDeSeguranca(HttpSecurity http,
            // O mesmo mecanismo que leva as exceções dos controllers para o TratadorDeErros.
            // Usando ele aqui, os erros 401/403 saem no mesmo formato (ProblemDetail) que o resto.
            @Qualifier("handlerExceptionResolver") HandlerExceptionResolver resolvedorDeErros) throws Exception {

        http
                // CSRF protege sites que usam cookie de sessão. Esta API usa token no cabeçalho,
                // que o navegador não envia sozinho, então a proteção não se aplica.
                .csrf(csrf -> csrf.disable())
                // STATELESS: o servidor não guarda sessão. Cada requisição se identifica pelo token.
                .sessionManagement(sessao -> sessao.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(rotas -> rotas
                        .requestMatchers(HttpMethod.POST, "/auth/login").permitAll()
                        .requestMatchers("/error").permitAll()
                        // Qualquer outra rota exige estar logado. Regra "fechado por padrão":
                        // uma rota nova nasce protegida, mesmo que alguém esqueça de configurar.
                        .anyRequest().authenticated())
                // "Resource server": quem confere o token de cada requisição.
                .oauth2ResourceServer(servidor -> servidor
                        // Ao aceitar um token, transforma o perfil dele em permissões.
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(conversorDeAutenticacao()))
                        .authenticationEntryPoint((req, resp, erro) -> resolvedorDeErros.resolveException(req, resp, null, erro)))
                .exceptionHandling(erros -> erros
                        .authenticationEntryPoint((req, resp, erro) -> resolvedorDeErros.resolveException(req, resp, null, erro))
                        .accessDeniedHandler((req, resp, erro) -> resolvedorDeErros.resolveException(req, resp, null, erro)));

        return http.build();
    }

    private JwtAuthenticationConverter conversorDeAutenticacao() {
        var conversor = new JwtAuthenticationConverter();
        conversor.setJwtGrantedAuthoritiesConverter(new ConversorDePermissoes());
        return conversor;
    }

    /** A chave secreta que assina (e confere) os tokens. Algoritmo HS256 (HMAC + SHA-256). */
    @Bean
    SecretKey chaveDoJwt(JwtPropriedades propriedades) {
        return new SecretKeySpec(propriedades.segredo().getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    /** Fabrica tokens (usado no login). */
    @Bean
    public JwtEncoder jwtEncoder(SecretKey chave) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(chave));
    }

    /** Confere tokens (usado em toda requisição). Recusa assinatura errada e token vencido. */
    @Bean
    public JwtDecoder jwtDecoder(SecretKey chave) {
        return NimbusJwtDecoder.withSecretKey(chave).macAlgorithm(MacAlgorithm.HS256).build();
    }

    /**
     * BCrypt transforma a senha num "hash": uma impressão digital que não dá para reverter.
     * - O mesmo texto gera hashes diferentes a cada vez (por causa do "sal" aleatório).
     * - Para conferir uma senha, usa-se matches(senhaDigitada, hashGuardado).
     * - Ele é lento DE PROPÓSITO: atrapalha quem tenta adivinhar milhões de senhas.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
