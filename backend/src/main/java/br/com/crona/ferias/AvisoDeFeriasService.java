package br.com.crona.ferias;

import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import br.com.crona.empresa.EmpresaAtual;
import br.com.crona.ferias.aviso.AvisoDeFerias;
import br.com.crona.ferias.aviso.GeradorDeAviso;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;

/**
 * Fase 8a: o aviso de férias (CLT, art. 135) em PDF, das MINHAS férias aprovadas.
 *
 * Aqui só se juntam os dados; quem desenha o PDF é o {@link GeradorDeAviso}.
 */
@Service
public class AvisoDeFeriasService {

    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final SimulacaoService simulacaoService;
    private final GeradorDeAviso gerador;
    private final EmpresaAtual empresaAtual;
    private final Clock relogio;

    public AvisoDeFeriasService(SolicitacaoFeriasRepository solicitacaoRepository,
                                SimulacaoService simulacaoService,
                                GeradorDeAviso gerador,
                                EmpresaAtual empresaAtual,
                                Clock relogio) {
        this.solicitacaoRepository = solicitacaoRepository;
        this.simulacaoService = simulacaoService;
        this.gerador = gerador;
        this.empresaAtual = empresaAtual;
        this.relogio = relogio;
    }

    /** O PDF e um nome de arquivo para o download. */
    public record Arquivo(String nome, byte[] conteudo) {
    }

    @Transactional(readOnly = true)
    public Arquivo gerar(Long solicitacaoId) {
        var eu = simulacaoService.colaboradorLogado();
        var pedido = solicitacaoRepository.findByIdAndColaboradorEmpresaId(solicitacaoId, empresaAtual.id())
                // Pedido de outra pessoa responde "não encontrado" (não revela que ele existe).
                .filter(s -> s.getColaborador().getId().equals(eu.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + solicitacaoId + " não encontrado."));
        if (pedido.getStatus() != StatusSolicitacao.APROVADA) {
            throw new RegraVioladaException("O aviso de férias só sai depois que as férias forem aprovadas.");
        }

        // A volta ao trabalho depende dos feriados: reaproveita a mesma avaliação da simulação.
        var comoSimulacao = new SimulacaoRequisicao(pedido.getDataInicio(), pedido.getDiasGozo(),
                pedido.getDiasAbono(), pedido.getPeriodoAquisitivo().getNumero());
        var volta = simulacaoService.avaliar(eu, comoSimulacao, pedido.getId()).resultado().voltaAoTrabalho();

        var empresa = eu.getEmpresa();
        var periodo = pedido.getPeriodoAquisitivo();
        var aviso = new AvisoDeFerias(empresa.getRazaoSocial(), empresa.getCnpj(), eu.getNome(), eu.getCargo(),
                eu.getDepartamento() == null ? null : eu.getDepartamento().getNome(), eu.getDataAdmissao(),
                periodo.getDataInicio(), periodo.getDataFim(),
                pedido.getDataInicio(), pedido.getDataFim(), pedido.getDiasGozo(), pedido.getDiasAbono(), volta,
                pedido.getGestorDecidiuPor() == null ? null : pedido.getGestorDecidiuPor().getNome(),
                LocalDate.now(relogio));

        return new Arquivo("aviso-de-ferias-" + pedido.getDataInicio() + ".pdf", gerador.gerarPdf(aviso));
    }
}
