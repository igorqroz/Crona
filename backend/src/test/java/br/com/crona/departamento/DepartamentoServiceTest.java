package br.com.crona.departamento;

import br.com.crona.compartilhado.erro.ConflitoException;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.empresa.Empresa;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.empresa.EmpresaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * TESTE UNITÁRIO: testa só o DepartamentoService, sem Spring, sem banco, sem servidor.
 *
 * O banco é trocado por "mocks" (Mockito): objetos falsos em que a gente diz o que
 * cada método responde. Por isso estes testes rodam em milissegundos.
 *
 * Cada teste segue o mesmo roteiro:
 *   dado   = prepara a situação
 *   quando = chama o método testado
 *   então  = confere o resultado
 */
@ExtendWith(MockitoExtension.class)
class DepartamentoServiceTest {

    private static final Long EMPRESA_ID = 1L;

    @Mock
    private DepartamentoRepository departamentoRepository;
    @Mock
    private EmpresaRepository empresaRepository;
    @Mock
    private EmpresaAtual empresaAtual;

    private DepartamentoService service;

    @BeforeEach
    void preparar() {
        service = new DepartamentoService(departamentoRepository, empresaRepository, empresaAtual);
        // lenient(): nem todo teste usa a empresa atual, e o Mockito reclamaria de "configuração sobrando".
        lenient().when(empresaAtual.id()).thenReturn(EMPRESA_ID);
    }

    @Test
    @DisplayName("criar: grava com o nome sem espaços nas pontas")
    void criarRemoveEspacos() {
        // dado
        when(departamentoRepository.existsByEmpresaIdAndNomeIgnoreCase(EMPRESA_ID, "Jurídico")).thenReturn(false);
        when(empresaRepository.getReferenceById(EMPRESA_ID)).thenReturn(mock(Empresa.class));
        when(departamentoRepository.save(any(Departamento.class))).thenAnswer(chamada -> chamada.getArgument(0));

        // quando
        var resposta = service.criar(new DepartamentoRequisicao("  Jurídico  "));

        // então
        assertThat(resposta.nome()).isEqualTo("Jurídico");
        // ArgumentCaptor "captura" o que foi passado ao save, para conferir o que iria para o banco.
        var gravado = ArgumentCaptor.forClass(Departamento.class);
        verify(departamentoRepository).save(gravado.capture());
        assertThat(gravado.getValue().getNome()).isEqualTo("Jurídico");
    }

    @Test
    @DisplayName("criar: recusa nome que já existe na empresa e não grava nada")
    void criarNomeRepetido() {
        when(departamentoRepository.existsByEmpresaIdAndNomeIgnoreCase(EMPRESA_ID, "Engenharia")).thenReturn(true);

        assertThatThrownBy(() -> service.criar(new DepartamentoRequisicao("Engenharia")))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("Engenharia");

        verify(departamentoRepository, never()).save(any());
    }

    @Test
    @DisplayName("renomear: muda o nome do departamento")
    void renomear() {
        var departamento = departamentoExistente(10L, "RH");
        when(departamentoRepository.findByIdAndEmpresaId(10L, EMPRESA_ID)).thenReturn(Optional.of(departamento));
        when(departamentoRepository.existsByEmpresaIdAndNomeIgnoreCaseAndIdNot(EMPRESA_ID, "Pessoas", 10L)).thenReturn(false);

        var resposta = service.renomear(10L, new DepartamentoRequisicao("Pessoas"));

        assertThat(resposta).isEqualTo(new DepartamentoResposta(10L, "Pessoas"));
        assertThat(departamento.getNome()).isEqualTo("Pessoas");
    }

    @Test
    @DisplayName("renomear: recusa o nome de outro departamento da empresa")
    void renomearParaNomeDeOutro() {
        when(departamentoRepository.findByIdAndEmpresaId(10L, EMPRESA_ID))
                .thenReturn(Optional.of(departamentoExistente(10L, "RH")));
        when(departamentoRepository.existsByEmpresaIdAndNomeIgnoreCaseAndIdNot(EMPRESA_ID, "Suporte", 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.renomear(10L, new DepartamentoRequisicao("Suporte")))
                .isInstanceOf(ConflitoException.class);
    }

    @Test
    @DisplayName("buscar: departamento de outra empresa responde 'não encontrado'")
    void buscarDeOutraEmpresa() {
        // O repository filtra por empresa: para a empresa 1, o id 99 (de outra empresa) não existe.
        when(departamentoRepository.findByIdAndEmpresaId(99L, EMPRESA_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.buscar(99L))
                .isInstanceOf(RecursoNaoEncontradoException.class)
                .hasMessageContaining("99");
    }

    @Test
    @DisplayName("excluir: departamento em uso vira conflito com mensagem clara")
    void excluirEmUso() {
        when(departamentoRepository.findByIdAndEmpresaId(10L, EMPRESA_ID))
                .thenReturn(Optional.of(departamentoExistente(10L, "Engenharia")));
        // Simula o banco recusando o DELETE por causa de uma chave estrangeira.
        doThrow(new DataIntegrityViolationException("FK")).when(departamentoRepository).flush();

        assertThatThrownBy(() -> service.excluir(10L))
                .isInstanceOf(ConflitoException.class)
                .hasMessageContaining("está em uso");
    }

    /** Cria um departamento "como se tivesse vindo do banco", já com id. */
    private Departamento departamentoExistente(Long id, String nome) {
        var departamento = new Departamento(mock(Empresa.class), nome);
        // O id não tem setter (quem gera é o banco). No teste, colocamos à força.
        ReflectionTestUtils.setField(departamento, "id", id);
        return departamento;
    }
}
