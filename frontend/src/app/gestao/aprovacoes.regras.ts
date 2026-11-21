/*
 * As regras da tela Aprovações (gestor), em funções puras (testadas em aprovacoes.regras.spec.ts).
 */
import { AusenciaNoCalendario, ItemDeAprovacao, SituacaoDoPedido, Verificacao } from '../nucleo/modelos';
import { diasEntre } from '../paginas/solicitar/calendario.regras';

export type Filtro = 'TODOS' | 'COM_CONFLITO' | 'SEM_CONFLITO';

export function filtrar(itens: ItemDeAprovacao[], filtro: Filtro): ItemDeAprovacao[] {
  switch (filtro) {
    case 'TODOS':
      return itens;
    case 'SEM_CONFLITO':
      return itens.filter((i) => i.situacao === 'SEM_CONFLITO');
    case 'COM_CONFLITO':
      return itens.filter((i) => i.situacao !== 'SEM_CONFLITO');
  }
}

export function contagens(itens: ItemDeAprovacao[]): Record<Filtro, number> {
  const sem = itens.filter((i) => i.situacao === 'SEM_CONFLITO').length;
  return { TODOS: itens.length, SEM_CONFLITO: sem, COM_CONFLITO: itens.length - sem };
}

// ------------------------------------------------------------------ o selo de cada pedido

/** Um nome curto para a regra que gerou o alerta, como no redesign ("Acima do limite"). */
const NOME_CURTO: Record<string, string> = {
  'EMP-01': 'Acima do limite',
  'EMP-03': 'Pouca antecedência',
  'EMP-04': 'Limite da empresa',
  'EMP-05': 'Período restrito',
  'CLT-03': 'Período vencido',
  'CLT-06': 'Abono fora do prazo',
};

/** A ordem de importância das regras para o selo (as da equipe primeiro). */
const PRIORIDADE = ['EMP-01', 'EMP-04', 'EMP-05', 'EMP-03', 'CLT-03', 'CLT-06'];

export type TomDoSelo = 'ok' | 'atencao' | 'impedimento';

export function selo(situacao: SituacaoDoPedido, alertas: Verificacao[]): { texto: string; tom: TomDoSelo } {
  if (situacao === 'SEM_CONFLITO') {
    return { texto: 'Sem conflito', tom: 'ok' };
  }
  const tom: TomDoSelo = situacao === 'COM_IMPEDIMENTO' ? 'impedimento' : 'atencao';
  // O que mais importa para o gestor primeiro: impedimento; depois as regras da equipe; depois as outras.
  const peso = (a: Verificacao) =>
    (a.resultado === 'IMPEDIMENTO' ? 0 : 100) + (PRIORIDADE.indexOf(a.regra) + 1 || PRIORIDADE.length + 1);
  const principal = [...alertas].sort((a, b) => peso(a) - peso(b))[0];
  const texto = principal
    ? (NOME_CURTO[principal.regra] ?? (tom === 'impedimento' ? 'Impedimento' : 'Ponto de atenção'))
    : 'Ponto de atenção';
  return { texto: alertas.length > 1 ? `${texto} +${alertas.length - 1}` : texto, tom };
}

/** O que o botão principal faz, conforme a situação do pedido. */
export function acaoPrincipal(situacao: SituacaoDoPedido): 'aprovar' | 'aprovar-mesmo-assim' | 'bloqueado' {
  switch (situacao) {
    case 'SEM_CONFLITO':
      return 'aprovar';
    case 'COM_ATENCAO':
      return 'aprovar-mesmo-assim';
    case 'COM_IMPEDIMENTO':
      return 'bloqueado';
  }
}

// ------------------------------------------------------------------ linha do tempo da equipe

export interface LinhaDoTempo {
  /** Os dias do pedido, em ISO. */
  dias: string[];
  /** Uma linha por pessoa: o colega (ou o próprio pedido) e em quais colunas ele está fora. */
  linhas: { nome: string; pendente: boolean; destaque: boolean; de: number; ate: number }[];
  /** Quantos fora em cada dia, CONTANDO o pedido, e se passa do limite. */
  foraNoDia: { total: number; acima: boolean }[];
}

/**
 * A linha do tempo do detalhe: só os dias do pedido, com os colegas que encostam neles.
 * As colunas (de/até) já vêm cortadas para caber no período do pedido.
 */
export function linhaDoTempo(
  pedido: { nome: string; inicio: string; fim: string },
  colegas: AusenciaNoCalendario[],
  limite: number | null,
): LinhaDoTempo {
  const total = diasEntre(pedido.inicio, pedido.fim);
  const [ano, mes, dia] = pedido.inicio.split('-').map(Number);
  const dias = Array.from({ length: total }, (_, i) => {
    const data = new Date(ano, mes - 1, dia + i);
    return `${data.getFullYear()}-${String(data.getMonth() + 1).padStart(2, '0')}-${String(data.getDate()).padStart(2, '0')}`;
  });

  const coluna = (iso: string) => diasEntre(pedido.inicio, iso) - 1;
  const linhas = colegas
    .filter((c) => c.inicio <= pedido.fim && c.fim >= pedido.inicio)
    .map((c) => ({
      nome: c.nome,
      pendente: c.pendente,
      destaque: false,
      de: Math.max(0, coluna(c.inicio)),
      ate: Math.min(total - 1, coluna(c.fim)),
    }));
  linhas.push({ nome: pedido.nome, pendente: true, destaque: true, de: 0, ate: total - 1 });

  const foraNoDia = dias.map((_, i) => {
    const fora = linhas.filter((l) => l.de <= i && l.ate >= i).length;
    return { total: fora, acima: limite !== null && fora > limite };
  });
  return { dias, linhas, foraNoDia };
}
