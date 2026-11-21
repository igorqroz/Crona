package br.com.crona.ferias;

import br.com.crona.compartilhado.erro.PedidoComImpedimentosException;
import br.com.crona.compartilhado.erro.RecursoNaoEncontradoException;
import br.com.crona.ferias.validacao.Verificacao;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * O lado do COLABORADOR: enviar, acompanhar e cancelar os próprios pedidos.
 * (O lado do gestor e do RH fica no AprovacaoService.)
 */
@Service
public class SolicitacaoService {

    private final SimulacaoService simulacaoService;
    private final SolicitacaoFeriasRepository solicitacaoRepository;
    private final PeriodoAquisitivoRepository periodoRepository;

    public SolicitacaoService(SimulacaoService simulacaoService,
                              SolicitacaoFeriasRepository solicitacaoRepository,
                              PeriodoAquisitivoRepository periodoRepository) {
        this.simulacaoService = simulacaoService;
        this.solicitacaoRepository = solicitacaoRepository;
        this.periodoRepository = periodoRepository;
    }

    /**
     * Envia o pedido: passa pela MESMA avaliação da simulação. Se houver impedimento, nada é
     * gravado e a resposta traz o checklist para a tela mostrar o que ajustar.
     * Pontos de atenção não impedem: o pedido vai para o gestor, que verá os avisos.
     */
    @Transactional
    public SolicitacaoResposta enviar(EnvioRequisicao envio) {
        var colaborador = simulacaoService.colaboradorLogado();

        // Trava os períodos ANTES de calcular o saldo (ver o comentário em travarDoColaborador).
        periodoRepository.travarDoColaborador(colaborador.getId());

        var avaliacao = simulacaoService.avaliar(colaborador, envio.comoSimulacao(), null);
        var resultado = avaliacao.resultado();
        if (!resultado.podeEnviar()) {
            int impedimentos = resultado.com(Verificacao.Resultado.IMPEDIMENTO).size();
            throw new PedidoComImpedimentosException(
                    "O pedido tem " + impedimentos + (impedimentos == 1 ? " impedimento" : " impedimentos")
                            + ". Ajuste as datas e tente de novo.",
                    resultado.verificacoes());
        }

        var pedido = new SolicitacaoFerias(colaborador, avaliacao.periodo().periodo(), resultado.inicio(),
                resultado.fim(), envio.diasGozo(), envio.diasAbono() == null ? 0 : envio.diasAbono(),
                envio.recado() == null || envio.recado().isBlank() ? null : envio.recado().trim());
        return SolicitacaoResposta.de(solicitacaoRepository.save(pedido));
    }

    @Transactional(readOnly = true)
    public List<SolicitacaoResposta> minhas() {
        var colaborador = simulacaoService.colaboradorLogado();
        return solicitacaoRepository.findByColaboradorIdOrderByDataInicioDesc(colaborador.getId()).stream()
                .map(SolicitacaoResposta::de)
                .toList();
    }

    @Transactional
    public SolicitacaoResposta cancelar(Long id) {
        var colaborador = simulacaoService.colaboradorLogado();
        var pedido = solicitacaoRepository.findById(id)
                // Pedido de outra pessoa responde "não encontrado", e não "proibido" (ver DepartamentoService).
                .filter(s -> s.getColaborador().getId().equals(colaborador.getId()))
                .orElseThrow(() -> new RecursoNaoEncontradoException("Pedido " + id + " não encontrado."));
        pedido.cancelar();
        return SolicitacaoResposta.de(pedido);
    }
}
