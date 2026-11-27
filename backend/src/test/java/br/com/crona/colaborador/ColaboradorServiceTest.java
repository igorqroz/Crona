package br.com.crona.colaborador;

import br.com.crona.compartilhado.erro.ConflitoException;
import br.com.crona.compartilhado.erro.ParametroInvalidoException;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import br.com.crona.departamento.DepartamentoRepository;
import br.com.crona.empresa.Empresa;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.empresa.EmpresaRepository;
import br.com.crona.seguranca.UsuarioAtual;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Teste unitário das regras de colaboradores. As regras do gestor são as mais
 * importantes, então ganharam um grupo próprio (@Nested).
 */
@ExtendWith(MockitoExtension.class)
class ColaboradorServiceTest {

    private static final Long EMPRESA_ID = 1L;
    private static final LocalDate ADMISSAO = LocalDate.of(2024, 2, 1);

    @Mock
    private ColaboradorRepository colaboradorRepository;
    @Mock
    private DepartamentoRepository departamentoRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private EmpresaAtual empresaAtual;
    @Mock
    private PasswordEncoder passwordEncoder;
    @Mock
    private ApplicationEventPublisher eventos;
    @Mock
    private UsuarioAtual usuarioAtual;

    private ColaboradorService service;

    @BeforeEach
    void preparar() {
        service = new ColaboradorService(colaboradorRepository, departamentoRepository,
                empresaRepository, empresaAtual, passwordEncoder, eventos, usuarioAtual);
        // Quem está logado é OUTRA pessoa do RH (id 999): a regra do BUG-06 é testada na API.
        lenient().when(usuarioAtual.id()).thenReturn(999L);
        lenient().when(empresaAtual.id()).thenReturn(EMPRESA_ID);
        lenient().when(empresaRepository.getReferenceById(EMPRESA_ID)).thenReturn(mock(Empresa.class));
        lenient().when(passwordEncoder.encode(any())).thenReturn("$2a$10$hashFalsoParaTeste");
        lenient().when(colaboradorRepository.save(any())).thenAnswer(chamada -> chamada.getArgument(0));
    }

    @Nested
    @DisplayName("cadastrar")
    class Cadastrar {

        @Test
        @DisplayName("guarda o e-mail em minúsculas e o HASH da senha, nunca a senha")
        void guardaHash() {
            service.cadastrar(cadastro(" Ana.Lima@Demo.Crona ", null));

            var gravado = ArgumentCaptor.forClass(Colaborador.class);
            verify(colaboradorRepository).save(gravado.capture());
            assertThat(gravado.getValue().getEmail()).isEqualTo("ana.lima@demo.crona");
            // senhaHash não tem getter (de propósito), então o teste lê o campo à força.
            assertThat(ReflectionTestUtils.getField(gravado.getValue(), "senhaHash"))
                    .isEqualTo("$2a$10$hashFalsoParaTeste")
                    .isNotEqualTo("segredo123");
            // E avisa que a admissão foi definida (para gerar os períodos aquisitivos).
            verify(eventos).publishEvent(any(AdmissaoDefinida.class));
        }

        @Test
        @DisplayName("recusa e-mail já usado e não grava nada")
        void emailRepetido() {
            when(colaboradorRepository.existsByEmailIgnoreCase("ana.lima@demo.crona")).thenReturn(true);

            assertThatThrownBy(() -> service.cadastrar(cadastro("ana.lima@demo.crona", null)))
                    .isInstanceOf(ConflitoException.class);
            verify(colaboradorRepository, never()).save(any());
        }

        @Test
        @DisplayName("recusa departamento que não é da empresa")
        void departamentoDeOutraEmpresa() {
            when(departamentoRepository.findByIdAndEmpresaId(50L, EMPRESA_ID)).thenReturn(Optional.empty());
            var dados = new ColaboradorCadastro("Ana", "ana@x.com", "segredo123", Perfil.COLABORADOR,
                    null, ADMISSAO, 50L, null);

            assertThatThrownBy(() -> service.cadastrar(dados))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("Departamento 50");
        }
    }

    @Nested
    @DisplayName("regras do gestor")
    class RegrasDoGestor {

        @Test
        @DisplayName("gestor precisa ter perfil GESTOR ou ADMIN")
        void gestorSemPerfil() {
            var ana = colaborador(3L, "Ana Lima", Perfil.COLABORADOR, null);
            when(colaboradorRepository.findByIdAndEmpresaId(3L, EMPRESA_ID)).thenReturn(Optional.of(ana));

            assertThatThrownBy(() -> service.cadastrar(cadastro("novo@x.com", 3L)))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("perfil COLABORADOR");
        }

        @Test
        @DisplayName("gestor desativado não pode receber equipe")
        void gestorDesativado() {
            var roberto = colaborador(2L, "Roberto Neves", Perfil.GESTOR, null);
            roberto.desativar();
            when(colaboradorRepository.findByIdAndEmpresaId(2L, EMPRESA_ID)).thenReturn(Optional.of(roberto));

            assertThatThrownBy(() -> service.cadastrar(cadastro("novo@x.com", 2L)))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("desativado");
        }

        @Test
        @DisplayName("ninguém é gestor de si mesmo")
        void gestorDeSiMesmo() {
            var roberto = colaborador(2L, "Roberto Neves", Perfil.GESTOR, null);
            when(colaboradorRepository.findByIdAndEmpresaId(2L, EMPRESA_ID)).thenReturn(Optional.of(roberto));

            assertThatThrownBy(() -> service.atualizar(2L, atualizacao(Perfil.GESTOR, 2L)))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("si mesmo");
        }

        @Test
        @DisplayName("recusa hierarquia em círculo: Roberto não pode ter como gestora a Paula, que é da equipe dele")
        void ciclo() {
            var roberto = colaborador(2L, "Roberto Neves", Perfil.GESTOR, null);
            var paula = colaborador(8L, "Paula Nunes", Perfil.GESTOR, roberto);   // Paula responde ao Roberto
            when(colaboradorRepository.findByIdAndEmpresaId(2L, EMPRESA_ID)).thenReturn(Optional.of(roberto));
            when(colaboradorRepository.findByIdAndEmpresaId(8L, EMPRESA_ID)).thenReturn(Optional.of(paula));

            assertThatThrownBy(() -> service.atualizar(2L, atualizacao(Perfil.GESTOR, 8L)))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("círculo");
        }

        @Test
        @DisplayName("aceita uma cadeia longa sem círculo")
        void cadeiaSemCiclo() {
            var diretora = colaborador(1L, "Diretora", Perfil.ADMIN, null);
            var gerente = colaborador(2L, "Gerente", Perfil.GESTOR, diretora);
            var ana = colaborador(3L, "Ana Lima", Perfil.COLABORADOR, null);
            when(colaboradorRepository.findByIdAndEmpresaId(3L, EMPRESA_ID)).thenReturn(Optional.of(ana));
            when(colaboradorRepository.findByIdAndEmpresaId(2L, EMPRESA_ID)).thenReturn(Optional.of(gerente));

            var resposta = service.atualizar(3L, atualizacao(Perfil.COLABORADOR, 2L));

            assertThat(resposta.gestor().nome()).isEqualTo("Gerente");
        }

        @Test
        @DisplayName("quem tem equipe não pode virar COLABORADOR")
        void perdePerfilTendoEquipe() {
            var roberto = colaborador(2L, "Roberto Neves", Perfil.GESTOR, null);
            when(colaboradorRepository.findByIdAndEmpresaId(2L, EMPRESA_ID)).thenReturn(Optional.of(roberto));
            when(colaboradorRepository.existsByGestorIdAndAtivoTrue(2L)).thenReturn(true);

            assertThatThrownBy(() -> service.atualizar(2L, atualizacao(Perfil.COLABORADOR, null)))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("Transfira a equipe");
        }
    }

    @Nested
    @DisplayName("desativar")
    class Desativar {

        @Test
        @DisplayName("recusa desativar quem ainda tem equipe")
        void comEquipe() {
            var roberto = colaborador(2L, "Roberto Neves", Perfil.GESTOR, null);
            when(colaboradorRepository.findByIdAndEmpresaId(2L, EMPRESA_ID)).thenReturn(Optional.of(roberto));
            when(colaboradorRepository.existsByGestorIdAndAtivoTrue(2L)).thenReturn(true);

            assertThatThrownBy(() -> service.desativar(2L)).isInstanceOf(RegraVioladaException.class);
            assertThat(roberto.isAtivo()).isTrue();
        }

        @Test
        @DisplayName("desativa quem não tem equipe")
        void semEquipe() {
            var ana = colaborador(3L, "Ana Lima", Perfil.COLABORADOR, null);
            when(colaboradorRepository.findByIdAndEmpresaId(3L, EMPRESA_ID)).thenReturn(Optional.of(ana));

            var resposta = service.desativar(3L);

            assertThat(resposta.ativo()).isFalse();
        }
    }

    @Nested
    @DisplayName("pesquisar")
    class Pesquisar {

        @Test
        @DisplayName("recusa ordenar por campo fora da lista (ex.: o hash da senha)")
        void ordenacaoProibida() {
            var paginacao = PageRequest.of(0, 20, Sort.by("senhaHash"));

            assertThatThrownBy(() -> service.pesquisar(null, null, null, paginacao))
                    .isInstanceOf(ParametroInvalidoException.class);
            verify(colaboradorRepository, never()).pesquisar(any(), any(), any(), any(), any());
        }

        @Test
        @DisplayName("sem busca, pesquisa com texto vazio (traz todos)")
        void semBusca() {
            var paginacao = PageRequest.of(0, 20, Sort.by("nome"));
            when(colaboradorRepository.pesquisar(EMPRESA_ID, "", null, null, paginacao)).thenReturn(Page.empty());

            var resultado = service.pesquisar(null, null, null, paginacao);

            assertThat(resultado.itens()).isEmpty();
            verify(colaboradorRepository).pesquisar(eq(EMPRESA_ID), eq(""), eq(null), eq(null), eq(paginacao));
        }
    }

    // ------------------------------------------------------------------ montadores de dados

    private static ColaboradorCadastro cadastro(String email, Long gestorId) {
        return new ColaboradorCadastro("Novo Colaborador", email, "segredo123", Perfil.COLABORADOR,
                null, ADMISSAO, null, gestorId);
    }

    private static ColaboradorAtualizacao atualizacao(Perfil perfil, Long gestorId) {
        return new ColaboradorAtualizacao("Nome", "pessoa@x.com", perfil, null, ADMISSAO, null, gestorId);
    }

    private static Colaborador colaborador(Long id, String nome, Perfil perfil, Colaborador gestor) {
        var c = new Colaborador(mock(Empresa.class), nome, nome.toLowerCase().replace(' ', '.') + "@x.com",
                "hash", perfil, null, ADMISSAO);
        c.alocar(null, gestor);
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}
