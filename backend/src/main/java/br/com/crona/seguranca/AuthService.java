package br.com.crona.seguranca;

import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.compartilhado.erro.CredenciaisInvalidasException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Locale;

@Service
public class AuthService {

    private final ColaboradorRepository colaboradorRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;

    /**
     * Um hash de mentira, só para gastar o mesmo tempo quando o e-mail não existe.
     * Sem isso, "e-mail inexistente" responderia mais rápido que "senha errada"
     * (porque pula o BCrypt), e medindo o tempo daria para descobrir quais e-mails têm conta.
     */
    private final String hashParaIgualarTempo;

    public AuthService(ColaboradorRepository colaboradorRepository, PasswordEncoder passwordEncoder,
                       TokenService tokenService) {
        this.colaboradorRepository = colaboradorRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.hashParaIgualarTempo = passwordEncoder.encode("senha-que-ninguem-usa");
    }

    @Transactional(readOnly = true)
    public LoginResposta entrar(LoginRequisicao login) {
        String email = login.email().trim().toLowerCase(Locale.ROOT);
        var encontrado = colaboradorRepository.findByEmailIgnoreCase(email);

        if (encontrado.isEmpty()) {
            passwordEncoder.matches(login.senha(), hashParaIgualarTempo);
            throw new CredenciaisInvalidasException();
        }

        var colaborador = encontrado.get();
        // Senha errada OU colaborador desativado: a mesma resposta, pelo mesmo motivo.
        if (!colaborador.senhaConfere(login.senha(), passwordEncoder) || !colaborador.isAtivo()) {
            throw new CredenciaisInvalidasException();
        }

        var token = tokenService.gerar(colaborador);
        return new LoginResposta(token.getTokenValue(), "Bearer", token.getExpiresAt(),
                new LoginResposta.Usuario(colaborador.getId(), colaborador.getNome(), colaborador.getPerfil()));
    }
}
