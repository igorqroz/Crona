package br.com.crona.ferias;

import java.time.LocalDate;
import java.util.List;

/**
 * Fase 8c: o "Quem está fora" do Painel do redesign, para o gestor (a equipe dele) e o RH (a empresa).
 *
 * @param departamentos só os que têm alguém fora no intervalo, cada um com quem está fora
 * @param restricoes    os períodos restritos (EMP-05); departamentoId nulo = empresa toda
 */
public record CalendarioDaGestao(
        LocalDate de,
        LocalDate ate,
        List<Departamento> departamentos,
        List<Restricao> restricoes,
        List<LocalDate> feriados
) {

    /**
     * @param ativos quantas pessoas ativas o departamento tem
     * @param limite EMP-01: quantas podem estar fora ao mesmo tempo (nulo = sem limite)
     */
    public record Departamento(Long id, String nome, int ativos, Integer limite, List<Pessoa> pessoas) {
    }

    public record Pessoa(Long id, String nome, List<Ausencia> ausencias) {
    }

    /** @param pendente true = ainda em análise (aparece listrado na tela) */
    public record Ausencia(Long pedidoId, LocalDate inicio, LocalDate fim, boolean pendente) {
    }

    public record Restricao(String motivo, Long departamentoId, LocalDate inicio, LocalDate fim, boolean bloqueia) {
    }
}
