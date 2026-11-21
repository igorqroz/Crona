package br.com.crona.compartilhado;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

import java.time.Clock;
import java.time.ZoneId;

/**
 * O relógio do sistema, num lugar só.
 *
 * Por que não usar LocalDate.now() direto?
 * 1. FUSO: o servidor pode estar em outro país. "Hoje" para as férias é sempre no horário
 *    de Brasília. Às 22h do dia 31 no Brasil, um servidor em UTC já está no dia 1º.
 * 2. TESTES: quem recebe um Clock pode ser testado em qualquer data (relógio "parado").
 */
@Configuration
@EnableScheduling   // liga as tarefas agendadas (@Scheduled), como a sincronização diária
public class RelogioConfig {

    public static final ZoneId FUSO = ZoneId.of("America/Sao_Paulo");

    @Bean
    public Clock relogio() {
        return Clock.system(FUSO);
    }
}
