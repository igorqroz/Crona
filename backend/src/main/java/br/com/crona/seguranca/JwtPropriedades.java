package br.com.crona.seguranca;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Lê a configuração crona.jwt.* do application.yml para dentro de um objeto Java.
 *
 * {@code @ConfigurationProperties}: em vez de vários @Value soltos, um record com tudo
 * junto, conferido quando a aplicação sobe.
 */
@ConfigurationProperties(prefix = "crona.jwt")
public record JwtPropriedades(String segredo, Duration validade) {

    public JwtPropriedades {
        // HS256 exige uma chave de pelo menos 256 bits (32 bytes). Uma chave curta seria fácil
        // de adivinhar; por isso a aplicação se recusa a subir com ela.
        if (segredo == null || segredo.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException(
                    "crona.jwt.segredo precisa ter pelo menos 32 caracteres (variável JWT_SEGREDO).");
        }
        if (validade == null) {
            validade = Duration.ofHours(8);
        }
    }
}
