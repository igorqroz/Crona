package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.colaborador.Perfil;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import br.com.crona.empresa.Empresa;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * TDD do ciclo de vida do pedido: cada transição permitida, e as proibidas.
 * Uma "máquina de estados" assim é um dos lugares onde mais aparecem bugs: um pedido
 * recusado que volta a ser aprovado, um cancelado que é confirmado pelo RH...
 */
class SolicitacaoFeriasTest {

    private static final Instant AGORA = Instant.parse("2026-02-10T13:00:00Z");

    private final Colaborador ana = pessoa(3L, "Ana Lima", Perfil.COLABORADOR);
    private final Colaborador roberto = pessoa(2L, "Roberto Neves", Perfil.GESTOR);
    private final Colaborador rafael = pessoa(1L, "Rafael Costa", Perfil.ADMIN);

    private final SolicitacaoFerias pedido = new SolicitacaoFerias(ana, mock(PeriodoAquisitivo.class),
            LocalDate.of(2026, 3, 23), LocalDate.of(2026, 4, 6), 15, 0, "Viagem já comprada");

    @Test
    @DisplayName("um pedido novo começa esperando o gestor")
    void comecaComOGestor() {
        assertThat(pedido.getStatus()).isEqualTo(StatusSolicitacao.AGUARDANDO_GESTOR);
        assertThat(pedido.getRecadoColaborador()).isEqualTo("Viagem já comprada");
    }

    @Nested
    @DisplayName("aprovação do gestor")
    class AprovacaoDoGestor {

        @Test
        @DisplayName("sem confirmação do RH (EMP-06 desligada): já fica APROVADA")
        void semRh() {
            pedido.aprovarPeloGestor(roberto, null, false, AGORA);

            assertThat(pedido.getStatus()).isEqualTo(StatusSolicitacao.APROVADA);
            assertThat(pedido.getGestorDecidiuPor()).isSameAs(roberto);
            assertThat(pedido.getGestorDecidiuEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("com confirmação do RH (EMP-06 ligada): vai para AGUARDANDO_RH")
        void comRh() {
            pedido.aprovarPeloGestor(roberto, "Ok, boa viagem", true, AGORA);

            assertThat(pedido.getStatus()).isEqualTo(StatusSolicitacao.AGUARDANDO_RH);
            assertThat(pedido.getMensagemDecisao()).isEqualTo("Ok, boa viagem");
        }

        @Test
        @DisplayName("ninguém aprova o próprio pedido")
        void proprioPedido() {
            assertThatThrownBy(() -> pedido.aprovarPeloGestor(ana, null, false, AGORA))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("próprio");
        }

        @Test
        @DisplayName("um pedido já decidido não pode ser aprovado de novo")
        void jaDecidido() {
            pedido.recusar(roberto, "Equipe no limite", AGORA);

            assertThatThrownBy(() -> pedido.aprovarPeloGestor(roberto, null, false, AGORA))
                    .isInstanceOf(RegraVioladaException.class);
        }
    }

    @Nested
    @DisplayName("recusa")
    class Recusa {

        @Test
        @DisplayName("o gestor recusa com um motivo: RECUSADA, e o motivo fica guardado")
        void recusa() {
            pedido.recusar(roberto, "Equipe no limite em março", AGORA);

            assertThat(pedido.getStatus()).isEqualTo(StatusSolicitacao.RECUSADA);
            assertThat(pedido.getMensagemDecisao()).isEqualTo("Equipe no limite em março");
        }

        @Test
        @DisplayName("recusar sem explicar o motivo não pode (o colaborador precisa saber por quê)")
        void semMotivo() {
            assertThatThrownBy(() -> pedido.recusar(roberto, "  ", AGORA))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("motivo");
        }

        @Test
        @DisplayName("o RH também pode recusar um pedido que está com ele")
        void rhRecusa() {
            pedido.aprovarPeloGestor(roberto, null, true, AGORA);
            pedido.recusar(rafael, "Fechamento da folha", AGORA);

            assertThat(pedido.getStatus()).isEqualTo(StatusSolicitacao.RECUSADA);
        }
    }

    @Nested
    @DisplayName("confirmação do RH")
    class ConfirmacaoDoRh {

        @Test
        @DisplayName("confirma um pedido que está com o RH: APROVADA")
        void confirma() {
            pedido.aprovarPeloGestor(roberto, null, true, AGORA);
            pedido.confirmarNoRh(rafael, AGORA);

            assertThat(pedido.getStatus()).isEqualTo(StatusSolicitacao.APROVADA);
            assertThat(pedido.getRhConfirmouPor()).isSameAs(rafael);
        }

        @Test
        @DisplayName("não dá para o RH confirmar antes de o gestor aprovar")
        void antesDoGestor() {
            assertThatThrownBy(() -> pedido.confirmarNoRh(rafael, AGORA))
                    .isInstanceOf(RegraVioladaException.class);
        }
    }

    @Nested
    @DisplayName("cancelamento pelo colaborador")
    class Cancelamento {

        @Test
        @DisplayName("cancela enquanto espera o gestor")
        void esperandoGestor() {
            pedido.cancelar();

            assertThat(pedido.getStatus()).isEqualTo(StatusSolicitacao.CANCELADA);
        }

        @Test
        @DisplayName("cancela enquanto espera o RH")
        void esperandoRh() {
            pedido.aprovarPeloGestor(roberto, null, true, AGORA);
            pedido.cancelar();

            assertThat(pedido.getStatus()).isEqualTo(StatusSolicitacao.CANCELADA);
        }

        @Test
        @DisplayName("pedido já APROVADO não se cancela por aqui (o pagamento pode já ter saído, CLT-08)")
        void jaAprovado() {
            pedido.aprovarPeloGestor(roberto, null, false, AGORA);

            assertThatThrownBy(pedido::cancelar).isInstanceOf(RegraVioladaException.class);
        }

        @Test
        @DisplayName("pedido recusado não se cancela")
        void recusado() {
            pedido.recusar(roberto, "Não dá", AGORA);

            assertThatThrownBy(pedido::cancelar).isInstanceOf(RegraVioladaException.class);
        }
    }

    private static Colaborador pessoa(Long id, String nome, Perfil perfil) {
        var c = new Colaborador(mock(Empresa.class), nome, id + "@x.com", "hash", perfil, null, LocalDate.of(2020, 1, 1));
        ReflectionTestUtils.setField(c, "id", id);
        return c;
    }
}
