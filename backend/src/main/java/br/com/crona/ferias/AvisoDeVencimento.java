package br.com.crona.ferias;

import br.com.crona.colaborador.Colaborador;
import br.com.crona.colaborador.ColaboradorRepository;
import br.com.crona.notificacao.Notificacoes;
import br.com.crona.notificacao.TipoDeNotificacao;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;

/**
 * Rotina diária (PAD-03): avisa quem tem dias de férias para tirar e o prazo (CLT-03) está acabando.
 *
 * O aviso sai 3 vezes por período, quando faltam 90, 60 e 30 dias. Cada "marco" tem uma
 * chave única (ex.: "FERIAS_A_VENCER:12:60"), então rodar a rotina duas vezes no mesmo dia,
 * ou dias seguidos dentro do mesmo marco, não repete o aviso (armadilha A-05).
 */
@Component
public class AvisoDeVencimento {

    private static final Logger log = LoggerFactory.getLogger(AvisoDeVencimento.class);
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ColaboradorRepository colaboradorRepository;
    private final PeriodoAquisitivoService periodoService;
    private final Notificacoes notificacoes;
    private final Clock relogio;

    AvisoDeVencimento(ColaboradorRepository colaboradorRepository, PeriodoAquisitivoService periodoService,
                      Notificacoes notificacoes, Clock relogio) {
        this.colaboradorRepository = colaboradorRepository;
        this.periodoService = periodoService;
        this.notificacoes = notificacoes;
        this.relogio = relogio;
    }

    // Todo dia às 7h (horário de Brasília): de manhã, para a pessoa ver o aviso ao começar o dia.
    @Scheduled(cron = "0 0 7 * * *", zone = "America/Sao_Paulo")
    void todoDia() {
        try {
            int avisos = avisar(LocalDate.now(relogio));
            log.info("Avisos de férias a vencer: {} novos.", avisos);
        } catch (RuntimeException e) {
            log.error("Falha nos avisos de férias a vencer.", e);
        }
    }

    /**
     * @param hoje recebido por parâmetro para os testes poderem "viajar no tempo"
     * @return quantos avisos novos saíram
     */
    @Transactional
    public int avisar(LocalDate hoje) {
        int novos = 0;
        for (Colaborador colaborador : colaboradorRepository.findByAtivoTrue()) {
            for (var p : periodoService.periodosComSaldo(colaborador.getId())) {
                var periodo = p.periodo();
                long faltam = ChronoUnit.DAYS.between(hoje, periodo.getDataLimiteConcessao());
                Integer marco = marco(faltam);
                int livres = p.saldo().livres();
                if (marco == null || livres <= 0) {
                    continue;
                }
                String mensagem = "Suas férias do período " + periodo.getDataInicio().format(DATA) + " a "
                        + periodo.getDataFim().format(DATA) + " vencem em "
                        + periodo.getDataLimiteConcessao().format(DATA) + " (faltam " + faltam + " dias). Você ainda tem "
                        + livres + (livres == 1 ? " dia" : " dias") + " para marcar.";
                if (notificacoes.criarUmaVez(colaborador, TipoDeNotificacao.FERIAS_A_VENCER, mensagem, "/solicitar",
                        "FERIAS_A_VENCER:" + periodo.getId() + ":" + marco)) {
                    novos++;
                }
            }
        }
        return novos;
    }

    /** O marco do aviso: 90, 60 ou 30 dias antes do prazo; nulo se ainda falta muito ou o prazo já passou. */
    static Integer marco(long diasQueFaltam) {
        if (diasQueFaltam <= 0 || diasQueFaltam > 90) return null;
        if (diasQueFaltam <= 30) return 30;
        if (diasQueFaltam <= 60) return 60;
        return 90;
    }
}
