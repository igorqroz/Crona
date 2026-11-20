package br.com.crona.seguranca;

import br.com.crona.colaborador.Perfil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * O mapa perfil → permissões é uma regra de negócio como outra qualquer: se alguém mudar
 * sem querer, um destes testes quebra e avisa.
 */
class PermissaoTest {

    @Test
    @DisplayName("COLABORADOR só vê departamentos")
    void colaborador() {
        assertThat(Permissao.doPerfil(Perfil.COLABORADOR)).containsExactly(Permissao.DEPARTAMENTO_VER);
    }

    @Test
    @DisplayName("GESTOR consulta, mas não gerencia nada")
    void gestor() {
        assertThat(Permissao.doPerfil(Perfil.GESTOR))
                .contains(Permissao.DEPARTAMENTO_VER, Permissao.COLABORADOR_VER)
                .doesNotContain(Permissao.DEPARTAMENTO_GERENCIAR, Permissao.COLABORADOR_GERENCIAR);
    }

    @Test
    @DisplayName("ADMIN tem todas as permissões")
    void admin() {
        assertThat(Permissao.doPerfil(Perfil.ADMIN)).containsExactlyInAnyOrder(Permissao.values());
    }

    @Test
    @DisplayName("token com perfil desconhecido ou sem perfil não ganha permissão nenhuma")
    void perfilDesconhecido() {
        var conversor = new ConversorDePermissoes();

        assertThat(conversor.convert(token("CHEFE_SUPREMO"))).isEmpty();
        assertThat(conversor.convert(token(null))).isEmpty();
    }

    private static Jwt token(String perfil) {
        var construtor = Jwt.withTokenValue("x").header("alg", "HS256").subject("1");
        if (perfil != null) {
            construtor.claim("perfil", perfil);
        }
        return construtor.build();
    }
}
