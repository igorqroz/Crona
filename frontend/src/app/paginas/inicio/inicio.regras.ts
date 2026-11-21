/*
 * As decisões da tela Início, em funções PURAS: recebem dados e devolvem dados, sem chamar a
 * API nem mexer na tela. Por isso dá para testá-las sozinhas (inicio.regras.spec.ts), do mesmo
 * jeito que as classes de domínio do back-end.
 */
import { diaMesHora, intervalo } from '../../nucleo/datas';
import { Negociacao, PeriodoAquisitivo, Referencia, Solicitacao } from '../../nucleo/modelos';

// ------------------------------------------------------------------ saldo

// O período do cartão e o número grande vêm de ferias/periodos.ts (a tela Solicitar também usa).
export { periodoEmFoco, saldo } from '../../ferias/periodos';

export type Bloco = 'aprovado' | 'pedido' | 'livre';

/** A barra de quadradinhos: um por dia de direito, na ordem aprovados → em pedido → livres. */
export function blocos(periodo: PeriodoAquisitivo): Bloco[] {
  return [
    ...Array<Bloco>(periodo.aprovados).fill('aprovado'),
    ...Array<Bloco>(periodo.emPedido).fill('pedido'),
    ...Array<Bloco>(periodo.livres).fill('livre'),
  ];
}

// ------------------------------------------------------------------ pedido

/**
 * O pedido que o cartão "Seu pedido" acompanha: o que está em análise (o que começa antes),
 * ou então as próximas férias aprovadas que ainda não terminaram.
 *
 * @param hoje "2026-11-21" (as datas no formato ISO podem ser comparadas como texto)
 */
export function pedidoEmFoco(solicitacoes: Solicitacao[], hoje: string): Solicitacao | null {
  const porInicio = (a: Solicitacao, b: Solicitacao) => a.inicio.localeCompare(b.inicio);
  const emAnalise = solicitacoes
    .filter((s) => s.status === 'AGUARDANDO_GESTOR' || s.status === 'AGUARDANDO_RH')
    .sort(porInicio);
  if (emAnalise.length > 0) {
    return emAnalise[0];
  }
  return solicitacoes.filter((s) => s.status === 'APROVADA' && s.fim >= hoje).sort(porInicio)[0] ?? null;
}

export interface Passo {
  titulo: string;
  detalhe: string;
  estado: 'feito' | 'atual' | 'pendente';
}

/** A linha do tempo do pedido: enviado → gestor → (RH) → aprovado. */
export function passosDoPedido(pedido: Solicitacao): Passo[] {
  const passos: Passo[] = [{ titulo: 'Enviado', detalhe: diaMesHora(pedido.enviadoEm), estado: 'feito' }];

  passos.push(
    pedido.status === 'AGUARDANDO_GESTOR'
      ? { titulo: 'Com o seu gestor', detalhe: 'aguardando a análise', estado: 'atual' }
      : { titulo: 'Aprovado pelo gestor', detalhe: 'seu gestor aprovou', estado: 'feito' },
  );

  // A etapa do RH só existe quando a empresa liga a EMP-06.
  if (pedido.status === 'AGUARDANDO_RH') {
    passos.push({ titulo: 'Com o RH', detalhe: 'confirmação do RH', estado: 'atual' });
  }

  passos.push(
    pedido.status === 'APROVADA'
      ? { titulo: 'Férias aprovadas', detalhe: 'tudo certo, boas férias!', estado: 'feito' }
      : { titulo: 'Férias aprovadas', detalhe: 'depois da análise', estado: 'pendente' },
  );
  return passos;
}

// ------------------------------------------------------------------ proposta de troca

export interface PropostaParaMim {
  negociacao: Negociacao;
  autor: Referencia;
  /** As minhas férias hoje (nulo se eu propus sem ter férias marcadas). */
  minhasHoje: string | null;
  /** Com quais datas eu ficaria se aceitar. */
  minhasNovas: string;
  mensagem: string | null;
  feitaEm: string;
  contraproposta: boolean;
}

/**
 * A troca que está esperando a MINHA resposta (é a minha vez), já "traduzida" para o meu lado:
 * se eu sou o colega, as minhas datas são as do colega; se eu propus, são as do solicitante.
 */
export function propostaParaMim(negociacoes: Negociacao[], meuId: number): PropostaParaMim | null {
  const negociacao = negociacoes.find((n) => n.vezDe?.id === meuId);
  const vigente = negociacao?.propostas.at(-1);
  if (!negociacao || !vigente) {
    return null;
  }
  const souOColega = negociacao.colega.id === meuId;
  const hoje = souOColega ? negociacao.pedidoDoColega : negociacao.pedidoDoSolicitante;
  return {
    negociacao,
    autor: vigente.autor,
    minhasHoje: hoje ? intervalo(hoje.inicio, hoje.fim) : null,
    minhasNovas: souOColega
      ? intervalo(vigente.inicioColega, vigente.fimColega)
      : intervalo(vigente.inicioSolicitante, vigente.fimSolicitante),
    mensagem: vigente.mensagem,
    feitaEm: vigente.feitaEm,
    contraproposta: negociacao.propostas.length > 1,
  };
}
