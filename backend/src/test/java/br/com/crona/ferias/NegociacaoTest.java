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
 * TDD da negociação de troca. O cenário: a Ana quer as férias de março do Bruno e oferece
 * a ele as de abril, que são dela. Os dois têm o Roberto como gestor.
 *
 * Regras decididas na Fase 6:
 * - só dá para propor troca de férias já APROVADAS;
 * - cada um mantém a quantidade de dias do próprio pedido (troca de datas, não de saldo);
 * - só entre colegas com o MESMO gestor, que é quem aprova;
 * - contrapropostas sem limite: qualquer um pode recusar e encerrar.
 */
class NegociacaoTest {

    private static final Instant AGORA = Instant.parse("2026-02-10T13:00:00Z");
    private static final LocalDate MARCO = LocalDate.of(2026, 3, 2);
    private static final LocalDate ABRIL = LocalDate.of(2026, 4, 6);

    private final Colaborador rafael = pessoa(1L, "Rafael Costa", Perfil.ADMIN, null);
    private final Colaborador roberto = pessoa(2L, "Roberto Neves", Perfil.GESTOR, rafael);
    private final Colaborador ana = pessoa(3L, "Ana Lima", Perfil.COLABORADOR, roberto);
    private final Colaborador bruno = pessoa(4L, "Bruno Teles", Perfil.COLABORADOR, roberto);
    private final Colaborador marina = pessoa(5L, "Marina Jorge", Perfil.COLABORADOR, rafael);

    /** Bruno: 15 dias em março, aprovados. */
    private final SolicitacaoFerias marcoDoBruno = aprovado(bruno, MARCO, 15);
    /** Ana: 15 dias em abril, aprovados. */
    private final SolicitacaoFerias abrilDaAna = aprovado(ana, ABRIL, 15);

    /** "Eu fico com março e você com abril." */
    private Negociacao anaPropoe() {
        return new Negociacao(ana, marcoDoBruno, abrilDaAna, MARCO, null, ABRIL, "Casamento da minha irmã");
    }

    @Nested
    @DisplayName("propor")
    class Propor {

        @Test
        @DisplayName("começa esperando o colega, com a 1ª proposta feita por quem propôs")
        void comecaComOColega() {
            var negociacao = anaPropoe();

            assertThat(negociacao.getStatus()).isEqualTo(StatusNegociacao.AGUARDANDO_COLEGA);
            assertThat(negociacao.getColega()).isSameAs(bruno);
            assertThat(negociacao.vezDe()).isSameAs(bruno);
            assertThat(negociacao.getPropostas()).hasSize(1);

            var proposta = negociacao.propostaVigente();
            assertThat(proposta.getAutor()).isSameAs(ana);
            assertThat(proposta.getMensagem()).isEqualTo("Casamento da minha irmã");
            // Cada um mantém os 15 dias: o fim é calculado a partir do início.
            assertThat(proposta.getFimSolicitante()).isEqualTo(LocalDate.of(2026, 3, 16));
            assertThat(proposta.getFimColega()).isEqualTo(LocalDate.of(2026, 4, 20));
        }

        @Test
        @DisplayName("quem ainda não tem férias marcadas informa quantos dias quer")
        void semPedidoProprio() {
            var negociacao = new Negociacao(ana, marcoDoBruno, null, MARCO, 10, ABRIL, null);

            assertThat(negociacao.diasDoSolicitante()).isEqualTo(10);
            assertThat(negociacao.propostaVigente().getFimSolicitante()).isEqualTo(LocalDate.of(2026, 3, 11));
        }

        @Test
        @DisplayName("sem férias marcadas e sem informar os dias: erro")
        void semPedidoESemDias() {
            assertThatThrownBy(() -> new Negociacao(ana, marcoDoBruno, null, MARCO, null, ABRIL, null))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("dias");
        }

        @Test
        @DisplayName("com férias marcadas, os dias são os do pedido: não dá para pedir outra quantidade")
        void mantemOsDias() {
            assertThatThrownBy(() -> new Negociacao(ana, marcoDoBruno, abrilDaAna, MARCO, 20, ABRIL, null))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("mantém");
        }

        @Test
        @DisplayName("só férias APROVADAS do colega podem ser trocadas")
        void soAprovadas() {
            var emAnalise = new SolicitacaoFerias(bruno, mock(PeriodoAquisitivo.class), MARCO,
                    MARCO.plusDays(14), 15, 0, null);

            assertThatThrownBy(() -> new Negociacao(ana, emAnalise, abrilDaAna, MARCO, null, ABRIL, null))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("aprovadas");
        }

        @Test
        @DisplayName("o pedido oferecido também precisa ser meu e estar aprovado")
        void pedidoOferecidoEMeu() {
            var outroDoBruno = aprovado(bruno, ABRIL, 15);

            assertThatThrownBy(() -> new Negociacao(ana, marcoDoBruno, outroDoBruno, MARCO, null, ABRIL, null))
                    .isInstanceOf(RegraVioladaException.class);
        }

        @Test
        @DisplayName("ninguém troca férias consigo mesmo")
        void consigoMesmo() {
            assertThatThrownBy(() -> new Negociacao(ana, abrilDaAna, null, MARCO, 15, ABRIL, null))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("você mesmo");
        }

        @Test
        @DisplayName("só entre colegas com o mesmo gestor")
        void mesmaEquipe() {
            var daMarina = aprovado(marina, MARCO, 15);

            assertThatThrownBy(() -> new Negociacao(ana, daMarina, abrilDaAna, MARCO, null, ABRIL, null))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("mesma equipe");
        }

        @Test
        @DisplayName("a proposta precisa mudar as férias do colega (senão não é troca)")
        void precisaMudarOColega() {
            assertThatThrownBy(() -> new Negociacao(ana, marcoDoBruno, null, ABRIL, 15, MARCO, null))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("mudar");
        }
    }

    @Nested
    @DisplayName("ir e voltar (contrapropostas)")
    class Rodadas {

        @Test
        @DisplayName("o colega contrapropõe: a vez passa para quem propôs, e os dias continuam os mesmos")
        void colegaContrapropoe() {
            var negociacao = anaPropoe();

            negociacao.contrapropor(bruno, MARCO.plusWeeks(1), ABRIL.plusWeeks(2), "Março só a partir do dia 9");

            assertThat(negociacao.getStatus()).isEqualTo(StatusNegociacao.AGUARDANDO_SOLICITANTE);
            assertThat(negociacao.vezDe()).isSameAs(ana);
            assertThat(negociacao.getPropostas()).hasSize(2);
            var vigente = negociacao.propostaVigente();
            assertThat(vigente.getAutor()).isSameAs(bruno);
            assertThat(vigente.getInicioSolicitante()).isEqualTo(LocalDate.of(2026, 3, 9));
            assertThat(vigente.getFimSolicitante()).isEqualTo(LocalDate.of(2026, 3, 23));
            assertThat(vigente.getFimColega()).isEqualTo(LocalDate.of(2026, 5, 4));
        }

        @Test
        @DisplayName("sem limite de rodadas: a conversa pode ir e voltar")
        void semLimite() {
            var negociacao = anaPropoe();

            negociacao.contrapropor(bruno, MARCO.plusWeeks(1), ABRIL, null);
            negociacao.contrapropor(ana, MARCO.plusWeeks(2), ABRIL, null);
            negociacao.contrapropor(bruno, MARCO.plusWeeks(3), ABRIL, null);

            assertThat(negociacao.getPropostas()).hasSize(4);
            assertThat(negociacao.getStatus()).isEqualTo(StatusNegociacao.AGUARDANDO_SOLICITANTE);
        }

        @Test
        @DisplayName("contraproposta igual à proposta vigente: erro (isso é aceitar)")
        void igualAVigente() {
            var negociacao = anaPropoe();

            assertThatThrownBy(() -> negociacao.contrapropor(bruno, MARCO, ABRIL, null))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("aceitar");
        }

        @Test
        @DisplayName("o colega aceita: vai para o gestor, e não é mais a vez de ninguém")
        void colegaAceita() {
            var negociacao = anaPropoe();

            negociacao.aceitar(bruno);

            assertThat(negociacao.getStatus()).isEqualTo(StatusNegociacao.AGUARDANDO_GESTOR);
            assertThat(negociacao.vezDe()).isNull();
        }

        @Test
        @DisplayName("quem propôs aceita a contraproposta: vai para o gestor")
        void solicitanteAceitaContraproposta() {
            var negociacao = anaPropoe();
            negociacao.contrapropor(bruno, MARCO.plusWeeks(1), ABRIL, null);

            negociacao.aceitar(ana);

            assertThat(negociacao.getStatus()).isEqualTo(StatusNegociacao.AGUARDANDO_GESTOR);
        }

        @Test
        @DisplayName("só responde quem está na vez: ninguém aceita a própria proposta")
        void foraDaVez() {
            var negociacao = anaPropoe();

            assertThatThrownBy(() -> negociacao.aceitar(ana))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("vez");
            assertThatThrownBy(() -> negociacao.contrapropor(marina, MARCO.plusWeeks(1), ABRIL, null))
                    .isInstanceOf(RegraVioladaException.class);
        }

        @Test
        @DisplayName("o colega recusa: RECUSADA, com quem recusou e o recado")
        void colegaRecusa() {
            var negociacao = anaPropoe();

            negociacao.recusar(bruno, "Já comprei as passagens", AGORA);

            assertThat(negociacao.getStatus()).isEqualTo(StatusNegociacao.RECUSADA);
            assertThat(negociacao.getDecididoPor()).isSameAs(bruno);
            assertThat(negociacao.getMensagemDecisao()).isEqualTo("Já comprei as passagens");
        }

        @Test
        @DisplayName("quem propôs pode desistir até o gestor decidir; o colega não cancela")
        void cancelar() {
            var negociacao = anaPropoe();
            negociacao.aceitar(bruno);

            assertThatThrownBy(() -> negociacao.cancelar(bruno))
                    .isInstanceOf(RegraVioladaException.class);

            negociacao.cancelar(ana);
            assertThat(negociacao.getStatus()).isEqualTo(StatusNegociacao.CANCELADA);
        }
    }

    @Nested
    @DisplayName("decisão do gestor")
    class Gestor {

        @Test
        @DisplayName("o gestor aprova a troca combinada: APROVADA")
        void aprova() {
            var negociacao = anaPropoe();
            negociacao.aceitar(bruno);

            negociacao.aprovarPeloGestor(roberto, null, AGORA);

            assertThat(negociacao.getStatus()).isEqualTo(StatusNegociacao.APROVADA);
            assertThat(negociacao.getDecididoPor()).isSameAs(roberto);
            assertThat(negociacao.getDecididoEm()).isEqualTo(AGORA);
        }

        @Test
        @DisplayName("antes de os dois concordarem, o gestor não decide")
        void antesDoAcordo() {
            var negociacao = anaPropoe();

            assertThatThrownBy(() -> negociacao.aprovarPeloGestor(roberto, null, AGORA))
                    .isInstanceOf(RegraVioladaException.class);
        }

        @Test
        @DisplayName("quem participa da troca não aprova a troca")
        void participanteNaoAprova() {
            var negociacao = anaPropoe();
            negociacao.aceitar(bruno);

            assertThatThrownBy(() -> negociacao.aprovarPeloGestor(ana, null, AGORA))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("participa");
        }

        @Test
        @DisplayName("recusar exige motivo")
        void recusa() {
            var negociacao = anaPropoe();
            negociacao.aceitar(bruno);

            assertThatThrownBy(() -> negociacao.recusarPeloGestor(roberto, " ", AGORA))
                    .isInstanceOf(RegraVioladaException.class)
                    .hasMessageContaining("motivo");

            negociacao.recusarPeloGestor(roberto, "Março é fechamento do trimestre", AGORA);
            assertThat(negociacao.getStatus()).isEqualTo(StatusNegociacao.RECUSADA);
            assertThat(negociacao.getMensagemDecisao()).isEqualTo("Março é fechamento do trimestre");
        }

        @Test
        @DisplayName("depois de decidida, a negociação não muda mais")
        void decididaNaoMuda() {
            var negociacao = anaPropoe();
            negociacao.aceitar(bruno);
            negociacao.aprovarPeloGestor(roberto, null, AGORA);

            assertThatThrownBy(() -> negociacao.cancelar(ana)).isInstanceOf(RegraVioladaException.class);
            assertThatThrownBy(() -> negociacao.recusarPeloGestor(roberto, "x", AGORA))
                    .isInstanceOf(RegraVioladaException.class);
        }
    }

    // ------------------------------------------------------------------ auxiliares

    private static Colaborador pessoa(Long id, String nome, Perfil perfil, Colaborador gestor) {
        var c = new Colaborador(mock(Empresa.class), nome, id + "@x.com", "hash", perfil, null, LocalDate.of(2020, 1, 1));
        ReflectionTestUtils.setField(c, "id", id);
        c.alocar(null, gestor);
        return c;
    }

    private SolicitacaoFerias aprovado(Colaborador quem, LocalDate inicio, int dias) {
        var pedido = new SolicitacaoFerias(quem, mock(PeriodoAquisitivo.class), inicio,
                inicio.plusDays(dias - 1L), dias, 0, null);
        pedido.aprovarPeloGestor(rafael, null, false, AGORA);
        return pedido;
    }
}
