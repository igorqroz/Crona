package br.com.crona.ferias;

/** Quanto um pedido usa de um período: dias de descanso + abono, e o status do pedido. */
public record ConsumoPorPeriodo(Long periodoId, Integer dias, StatusSolicitacao status) {
}
