package br.com.crona.ferias;

import br.com.crona.colaborador.AdmissaoDefinida;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * QUANDO os períodos aquisitivos são gerados ou atualizados:
 *
 * 1. Ao cadastrar um colaborador ou mudar a admissão dele (evento AdmissaoDefinida).
 * 2. Todo dia à 1h da manhã: quem fez aniversário de empresa ganha o período novo.
 * 3. Quando a API sobe: cobre os dias em que ela ficou desligada e os dados carregados
 *    direto no banco (como os dados de desenvolvimento).
 */
@Component
class SincronizacaoDePeriodos {

    private static final Logger log = LoggerFactory.getLogger(SincronizacaoDePeriodos.class);

    private final PeriodoAquisitivoService periodoService;

    SincronizacaoDePeriodos(PeriodoAquisitivoService periodoService) {
        this.periodoService = periodoService;
    }

    @EventListener
    void aoDefinirAdmissao(AdmissaoDefinida evento) {
        periodoService.sincronizar(evento.colaboradorId());
    }

    // cron: segundo minuto hora dia mês dia-da-semana  →  "todo dia, 01:00:00", no horário de Brasília
    @Scheduled(cron = "0 0 1 * * *", zone = "America/Sao_Paulo")
    void todoDia() {
        sincronizarTodos("rotina diária");
    }

    @EventListener(ApplicationReadyEvent.class)
    void aoIniciar() {
        sincronizarTodos("inicialização");
    }

    private void sincronizarTodos(String motivo) {
        try {
            periodoService.sincronizarTodos();
            log.info("Períodos aquisitivos sincronizados ({}).", motivo);
        } catch (RuntimeException e) {
            // Uma falha aqui não pode derrubar a API: registra e tenta de novo na próxima vez.
            log.error("Falha ao sincronizar períodos aquisitivos ({}).", motivo, e);
        }
    }
}
