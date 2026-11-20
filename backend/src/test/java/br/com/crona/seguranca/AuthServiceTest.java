package br.com.crona.seguranca;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.colaborador.Perfil;
import br.com.crona.compartilhado.erro.CredenciaisInvalidasException;
import br.com.crona.empresa.Empresa;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitário do login. Usa o BCrypt de verdade (é rápido o bastante com custo 4),
 * e mocks para o banco e para o gerador de token.
 */
class AuthServiceTest {

    private final ColaboradorRepository repository = mock(ColaboradorRepository.class);
    private final TokenService tokenService = mock(TokenService.class);
    // spy: um objeto de verdade, mas que o Mockito consegue "espionar" (contar chamadas).
    // Custo 4 em vez de 10: o teste fica rápido; a segurança do teste não importa.
    private final PasswordEncoder passwordEncoder = spy(new BCryptPasswordEncoder(4));

    private AuthService authService;
    private Colaborador ana;

    @BeforeEach
    void preparar() {
        authService = new AuthService(repository, passwordEncoder, tokenService);

        ana = new Colaborador(mock(Empresa.class), "Ana Lima", "ana.lima@demo.crona",
                passwordEncoder.encode("crona123"), Perfil.COLABORADOR, null, LocalDate.of(2024, 2, 1));
        ReflectionTestUtils.setField(ana, "id", 3L);

        var token = Jwt.withTokenValue("token.de.teste").header("alg", "HS256")
                .expiresAt(Instant.parse("2026-11-21T08:00:00Z")).claim("sub", "3").build();
        when(tokenService.gerar(any())).thenReturn(token);
    }

    @Test
    @DisplayName("e-mail e senha certos: devolve o token e os dados básicos do usuário")
    void loginCerto() {
        when(repository.findByEmailIgnoreCase("ana.lima@demo.crona")).thenReturn(Optional.of(ana));

        var resposta = authService.entrar(new LoginRequisicao(" Ana.Lima@Demo.Crona ", "crona123"));

        assertThat(resposta.token()).isEqualTo("token.de.teste");
        assertThat(resposta.tipo()).isEqualTo("Bearer");
        assertThat(resposta.usuario().nome()).isEqualTo("Ana Lima");
        assertThat(resposta.usuario().perfil()).isEqualTo(Perfil.COLABORADOR);
    }

    @Test
    @DisplayName("senha errada: 'E-mail ou senha inválidos' e nenhum token gerado")
    void senhaErrada() {
        when(repository.findByEmailIgnoreCase("ana.lima@demo.crona")).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> authService.entrar(new LoginRequisicao("ana.lima@demo.crona", "errada")))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        verify(tokenService, never()).gerar(any());
    }

    @Test
    @DisplayName("e-mail inexistente: a MESMA mensagem, e o BCrypt roda mesmo assim (mesmo tempo de resposta)")
    void emailInexistente() {
        when(repository.findByEmailIgnoreCase("ninguem@demo.crona")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.entrar(new LoginRequisicao("ninguem@demo.crona", "crona123")))
                .isInstanceOf(CredenciaisInvalidasException.class)
                .hasMessage("E-mail ou senha inválidos.");
        // Sem esta conferência, a resposta seria mais rápida e entregaria que o e-mail não existe.
        verify(passwordEncoder, times(1)).matches(anyString(), anyString());
    }

    @Test
    @DisplayName("colaborador desativado não entra, mesmo com a senha certa")
    void desativado() {
        ana.desativar();
        when(repository.findByEmailIgnoreCase("ana.lima@demo.crona")).thenReturn(Optional.of(ana));

        assertThatThrownBy(() -> authService.entrar(new LoginRequisicao("ana.lima@demo.crona", "crona123")))
                .isInstanceOf(CredenciaisInvalidasException.class);
        verify(tokenService, never()).gerar(any());
    }
}
