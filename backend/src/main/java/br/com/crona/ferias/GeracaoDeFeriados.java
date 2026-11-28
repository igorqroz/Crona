package br.com.crona.ferias;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;

/**
 * BUG-08: mantém os feriados nacionais cadastrados sempre à frente (o ano atual e mais 10),
 * para a CLT-05 enxergar os feriados de qualquer pedido. Roda quando a API sobe e todo dia,
 * então o horizonte anda sozinho. Não duplica: o que já existe (como 2026 e 2027, da V2) fica.
 */
@Component
class GeracaoDeFeriados {

    static final int ANOS_A_FRENTE = 10;

    private static final Logger log = LoggerFactory.getLogger(GeracaoDeFeriados.class);

    private final FeriadoRepository feriadoRepository;
    private final Clock relogio;

    GeracaoDeFeriados(FeriadoRepository feriadoRepository, Clock relogio) {
        this.feriadoRepository = feriadoRepository;
        this.relogio = relogio;
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoIniciar() {
        gerar();
    }

    // "todo dia, 00:30", no horário de Brasília: na virada do ano, o 10º ano à frente aparece.
    @Scheduled(cron = "0 30 0 * * *", zone = "America/Sao_Paulo")
    void todoDia() {
        gerar();
    }

    private void gerar() {
        try {
            int anoAtual = LocalDate.now(relogio).getYear();
            int novos = 0;
            for (int ano = anoAtual; ano <= anoAtual + ANOS_A_FRENTE; ano++) {
                for (var feriado : FeriadosNacionais.doAno(ano)) {
                    novos += feriadoRepository.inserirNacional(feriado.data(), feriado.descricao());
                }
            }
            if (novos > 0) {
                log.info("{} feriados nacionais cadastrados (até {}).", novos, anoAtual + ANOS_A_FRENTE);
            }
        } catch (RuntimeException e) {
            // Uma falha aqui não derruba a API: registra e tenta de novo no dia seguinte.
            log.error("Falha ao cadastrar os feriados nacionais.", e);
        }
    }
}
