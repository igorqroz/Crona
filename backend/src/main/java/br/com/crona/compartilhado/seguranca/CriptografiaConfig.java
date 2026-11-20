package br.com.crona.compartilhado.seguranca;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * {@code @Configuration} + {@code @Bean}: ensina o Spring a criar um objeto que qualquer
 * classe pode pedir no construtor (aqui, o PasswordEncoder).
 *
 * BCrypt transforma a senha num "hash": uma impressão digital que não dá para reverter.
 * - O mesmo texto gera hashes diferentes a cada vez (por causa do "sal" aleatório).
 * - Para conferir uma senha no login, usa-se matches(senhaDigitada, hashGuardado).
 * - Ele é lento DE PROPÓSITO: atrapalha quem tenta adivinhar milhões de senhas.
 */
@Configuration
public class CriptografiaConfig {

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
