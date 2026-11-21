package br.com.crona.ferias;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDate;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * A troca de férias de ponta a ponta, pela API.
 *
 * O cenário não depende da data de hoje: além da Bia (ComBiaTestBase), cada teste cria o "Caio",
 * também admitido há 18 meses e com o Roberto como gestor. A Bia tem férias numa segunda daqui a
 * ~45 dias; o Caio, umas 5 semanas depois. A Bia quer as datas do Caio, e oferece as dela.
 */
class NegociacaoApiTest extends ComBiaTestBase {

    @org.springframework.beans.factory.annotation.Autowired
    private jakarta.persistence.EntityManager entityManager;

    private Long caioId;
    /** Uma segunda livre umas 5 semanas depois da {@code segunda} da Bia. */
    private LocalDate depois;

    @BeforeEach
    void criarCaio() throws Exception {
        Long engenharia = jdbc.queryForObject("SELECT id FROM departamento WHERE nome = 'Engenharia' AND empresa_id = ?",
                Long.class, empresaDemo);
        var json = mockMvc.perform(post("/colaboradores").contentType(MediaType.APPLICATION_JSON).content("""
                        {"nome": "Caio Teste", "email": "caio.teste@demo.crona", "senha": "segredo123",
                         "perfil": "COLABORADOR", "dataAdmissao": "%s", "departamentoId": %d, "gestorId": %d}
                        """.formatted(LocalDate.now(br.com.crona.compartilhado.RelogioConfig.FUSO).minusMonths(18),
                        engenharia, robertoId)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        caioId = ((Number) JsonPath.read(json, "$.id")).longValue();
        depois = segundaLivreDepoisDe(segunda.plusWeeks(5));
    }

    // ------------------------------------------------------------------ auxiliares

    /** A pessoa pede férias e o Roberto aprova: devolve o id do pedido APROVADO. */
    private Long feriasAprovadas(Long quem, LocalDate inicio, int dias) throws Exception {
        var json = mockMvc.perform(post("/minhas-ferias/solicitacoes").with(como(quem, "COLABORADOR"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"inicio": "%s", "diasGozo": %d}
                                """.formatted(inicio, dias)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long pedido = ((Number) JsonPath.read(json, "$.id")).longValue();
        mockMvc.perform(post("/aprovacoes/" + pedido + "/aprovar").with(como(robertoId, "GESTOR")))
                .andExpect(jsonPath("$.status").value("APROVADA"));
        return pedido;
    }

    private ResultActions biaPropoe(String corpo) throws Exception {
        return mockMvc.perform(post("/minhas-ferias/negociacoes").with(como(biaId, "COLABORADOR"))
                .contentType(MediaType.APPLICATION_JSON).content(corpo));
    }

    /** "Eu fico com as suas férias (depois), e você fica com as minhas (segunda)." */
    private Long biaPropoeTrocar(Long pedidoDoCaio, Long pedidoDaBia) throws Exception {
        var json = biaPropoe("""
                {"pedidoDoColegaId": %d, "pedidoDoSolicitanteId": %d,
                 "inicioSolicitante": "%s", "inicioColega": "%s", "mensagem": "Casamento da minha irmã"}
                """.formatted(pedidoDoCaio, pedidoDaBia, depois, segunda))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions caio(String acao, Long negociacao) throws Exception {
        return mockMvc.perform(post("/minhas-ferias/negociacoes/" + negociacao + "/" + acao).with(como(caioId, "COLABORADOR")));
    }

    // ------------------------------------------------------------------ testes

    @Nested
    @DisplayName("fluxo da troca")
    class Fluxo {

        @Test
        @DisplayName("Bia propõe, Caio aceita, Roberto aprova: as datas dos dois são trocadas")
        void fluxoCompleto() throws Exception {
            Long daBia = feriasAprovadas(biaId, segunda, 15);
            Long doCaio = feriasAprovadas(caioId, depois, 15);

            // A Bia vê as férias do Caio entre as que dá para propor trocar.
            mockMvc.perform(get("/minhas-ferias/equipe").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[?(@.pedidoId == " + doCaio + ")].colaborador.nome").value("Caio Teste"));

            Long negociacao = biaPropoeTrocar(doCaio, daBia);

            mockMvc.perform(get("/minhas-ferias/negociacoes").with(como(caioId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].id").value(negociacao))
                    .andExpect(jsonPath("$[0].status").value("AGUARDANDO_COLEGA"))
                    .andExpect(jsonPath("$[0].vezDe.nome").value("Caio Teste"))
                    .andExpect(jsonPath("$[0].propostas[0].mensagem").value("Casamento da minha irmã"));

            caio("aceitar", negociacao)
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AGUARDANDO_GESTOR"));

            mockMvc.perform(get("/aprovacoes/negociacoes").with(como(robertoId, "GESTOR")))
                    .andExpect(jsonPath("$[?(@.negociacao.id == " + negociacao + ")].situacao").value("SEM_CONFLITO"));

            mockMvc.perform(post("/aprovacoes/negociacoes/" + negociacao + "/aprovar").with(como(robertoId, "GESTOR")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("APROVADA"))
                    .andExpect(jsonPath("$.decididoPor.nome").value("Roberto Neves"));

            mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].inicio").value(depois.toString()))
                    .andExpect(jsonPath("$[0].status").value("APROVADA"));
            mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(caioId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].inicio").value(segunda.toString()));
        }

        @Test
        @DisplayName("contraproposta: o Caio sugere outras datas e a Bia aceita")
        void contraproposta() throws Exception {
            Long negociacao = biaPropoeTrocar(feriasAprovadas(caioId, depois, 15), feriasAprovadas(biaId, segunda, 15));
            LocalDate maisTarde = segundaLivreDepoisDe(depois.plusWeeks(1));

            mockMvc.perform(post("/minhas-ferias/negociacoes/" + negociacao + "/contrapropor").with(como(caioId, "COLABORADOR"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {"inicioSolicitante": "%s", "inicioColega": "%s", "mensagem": "Só a partir dessa semana"}
                                    """.formatted(maisTarde, segunda)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("AGUARDANDO_SOLICITANTE"))
                    .andExpect(jsonPath("$.vezDe.nome").value("Bia Teste"))
                    .andExpect(jsonPath("$.propostas", hasSize(2)));

            mockMvc.perform(post("/minhas-ferias/negociacoes/" + negociacao + "/aceitar").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$.status").value("AGUARDANDO_GESTOR"));

            mockMvc.perform(post("/aprovacoes/negociacoes/" + negociacao + "/aprovar").with(como(robertoId, "GESTOR")))
                    .andExpect(jsonPath("$.status").value("APROVADA"));
            mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].inicio").value(maisTarde.toString()));
        }

        @Test
        @DisplayName("quem não tinha férias marcadas: o pedido dela nasce aprovado, com os dias que ela pediu")
        void semFeriasMarcadas() throws Exception {
            Long doCaio = feriasAprovadas(caioId, depois, 15);

            var json = biaPropoe("""
                    {"pedidoDoColegaId": %d, "inicioSolicitante": "%s", "diasSolicitante": 20, "inicioColega": "%s"}
                    """.formatted(doCaio, depois, segunda))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.pedidoDoSolicitante").doesNotExist())
                    .andReturn().getResponse().getContentAsString();
            Long negociacao = ((Number) JsonPath.read(json, "$.id")).longValue();

            caio("aceitar", negociacao);
            mockMvc.perform(post("/aprovacoes/negociacoes/" + negociacao + "/aprovar").with(como(robertoId, "GESTOR")))
                    .andExpect(jsonPath("$.status").value("APROVADA"))
                    .andExpect(jsonPath("$.pedidoDoSolicitante.diasGozo").value(20));

            mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$", hasSize(1)))
                    .andExpect(jsonPath("$[0].status").value("APROVADA"))
                    .andExpect(jsonPath("$[0].inicio").value(depois.toString()))
                    .andExpect(jsonPath("$[0].diasGozo").value(20));
        }

        @Test
        @DisplayName("o Caio recusa com recado: RECUSADA, e as férias dos dois não mudam")
        void colegaRecusa() throws Exception {
            Long doCaio = feriasAprovadas(caioId, depois, 15);
            Long negociacao = biaPropoeTrocar(doCaio, feriasAprovadas(biaId, segunda, 15));

            mockMvc.perform(post("/minhas-ferias/negociacoes/" + negociacao + "/recusar").with(como(caioId, "COLABORADOR"))
                            .contentType(MediaType.APPLICATION_JSON).content("""
                                    {"mensagem": "Já comprei as passagens"}
                                    """))
                    .andExpect(jsonPath("$.status").value("RECUSADA"))
                    .andExpect(jsonPath("$.mensagemDecisao").value("Já comprei as passagens"));

            mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(caioId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].inicio").value(depois.toString()));
        }
    }

    @Nested
    @DisplayName("regras")
    class Regras {

        @Test
        @DisplayName("datas com impedimento (início numa sexta, CLT-05): 422 com o checklist, e nada é gravado")
        void impedimento() throws Exception {
            Long doCaio = feriasAprovadas(caioId, depois, 15);
            Long daBia = feriasAprovadas(biaId, segunda, 15);

            biaPropoe("""
                    {"pedidoDoColegaId": %d, "pedidoDoSolicitanteId": %d, "inicioSolicitante": "%s", "inicioColega": "%s"}
                    """.formatted(doCaio, daBia, depois, segunda.plusDays(4)))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.verificacoes[?(@.regra == 'CLT-05' && @.resultado == 'IMPEDIMENTO')].mensagem")
                            .value(org.hamcrest.Matchers.contains(org.hamcrest.Matchers.startsWith("Caio Teste:"))));

            mockMvc.perform(get("/minhas-ferias/negociacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$", hasSize(0)));
        }

        @Test
        @DisplayName("as mesmas férias não entram em duas negociações abertas ao mesmo tempo (409)")
        void umaNegociacaoPorVez() throws Exception {
            Long doCaio = feriasAprovadas(caioId, depois, 15);
            Long daBia = feriasAprovadas(biaId, segunda, 15);
            biaPropoeTrocar(doCaio, daBia);

            biaPropoe("""
                    {"pedidoDoColegaId": %d, "inicioSolicitante": "%s", "diasSolicitante": 10, "inicioColega": "%s"}
                    """.formatted(doCaio, depois, segunda))
                    .andExpect(status().isConflict());
        }

        @Test
        @DisplayName("só com colega da mesma equipe: a Bia não troca com quem tem outro gestor (422)")
        void outraEquipe() throws Exception {
            Long doCaio = feriasAprovadas(caioId, depois, 15);
            Long rafael = idDoColaborador("rafael.costa@demo.crona");
            // O UPDATE vai direto no banco, mas o Hibernate guarda em memória o Caio que já leu
            // (cache de 1º nível). flush() grava o que estava pendente; clear() esquece o que foi lido.
            entityManager.flush();
            jdbc.update("UPDATE colaborador SET gestor_id = ? WHERE id = ?", rafael, caioId);
            entityManager.clear();

            biaPropoe("""
                    {"pedidoDoColegaId": %d, "inicioSolicitante": "%s", "diasSolicitante": 10, "inicioColega": "%s"}
                    """.formatted(doCaio, depois, segunda))
                    .andExpect(status().isUnprocessableContent())
                    .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.containsString("mesma equipe")));
        }

        @Test
        @DisplayName("quem não participa da negociação não a vê nem responde (404)")
        void quemNaoParticipa() throws Exception {
            Long negociacao = biaPropoeTrocar(feriasAprovadas(caioId, depois, 15), feriasAprovadas(biaId, segunda, 15));
            Long ana = idDoColaborador("ana.lima@demo.crona");

            mockMvc.perform(post("/minhas-ferias/negociacoes/" + negociacao + "/aceitar").with(como(ana, "COLABORADOR")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("ninguém aceita a própria proposta: a Bia não pode aceitar no lugar do Caio (422)")
        void foraDaVez() throws Exception {
            Long negociacao = biaPropoeTrocar(feriasAprovadas(caioId, depois, 15), feriasAprovadas(biaId, segunda, 15));

            mockMvc.perform(post("/minhas-ferias/negociacoes/" + negociacao + "/aceitar").with(como(biaId, "COLABORADOR")))
                    .andExpect(status().isUnprocessableContent());
        }
    }

    @Nested
    @DisplayName("gestor")
    class Gestor {

        private Long trocaCombinada() throws Exception {
            Long negociacao = biaPropoeTrocar(feriasAprovadas(caioId, depois, 15), feriasAprovadas(biaId, segunda, 15));
            caio("aceitar", negociacao).andExpect(status().isOk());
            return negociacao;
        }

        @Test
        @DisplayName("recusar exige motivo; recusada, as férias dos dois não mudam")
        void recusa() throws Exception {
            Long negociacao = trocaCombinada();
            String url = "/aprovacoes/negociacoes/" + negociacao + "/recusar";

            mockMvc.perform(post(url).with(como(robertoId, "GESTOR"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"mensagem\": \" \"}"))
                    .andExpect(status().isUnprocessableContent());

            mockMvc.perform(post(url).with(como(robertoId, "GESTOR"))
                            .contentType(MediaType.APPLICATION_JSON).content("{\"mensagem\": \"Entrega do projeto\"}"))
                    .andExpect(jsonPath("$.status").value("RECUSADA"));

            mockMvc.perform(get("/minhas-ferias/solicitacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(jsonPath("$[0].inicio").value(segunda.toString()));
        }

        @Test
        @DisplayName("um gestor de OUTRA equipe não vê nem aprova a troca (404)")
        void outroGestor() throws Exception {
            Long negociacao = trocaCombinada();
            Long marina = idDoColaborador("marina.jorge@demo.crona");
            jdbc.update("UPDATE colaborador SET perfil = 'GESTOR' WHERE id = ?", marina);

            mockMvc.perform(get("/aprovacoes/negociacoes").with(como(marina, "GESTOR")))
                    .andExpect(jsonPath("$", hasSize(0)));
            mockMvc.perform(post("/aprovacoes/negociacoes/" + negociacao + "/aprovar").with(como(marina, "GESTOR")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("COLABORADOR não acessa a fila de trocas do gestor (403)")
        void colaborador() throws Exception {
            mockMvc.perform(get("/aprovacoes/negociacoes").with(como(biaId, "COLABORADOR")))
                    .andExpect(status().isForbidden());
        }
    }
}
