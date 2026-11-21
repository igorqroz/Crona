/*
 * As regras da tela Trocas de período, em funções puras (testadas em trocas.regras.spec.ts).
 *
 * O ponto principal: a API fala do ponto de vista da NEGOCIAÇÃO (solicitante = quem propôs,
 * colega = quem recebeu). A tela fala do ponto de vista de QUEM ESTÁ LOGADO ("você" e "o outro").
 * Estas funções fazem essa tradução, nos dois sentidos.
 */
import { intervalo } from '../../nucleo/datas';
import { Contraproposta, Negociacao, Referencia, Solicitacao, StatusNegociacao } from '../../nucleo/modelos';

export type TomDoStatus = 'acao' | 'espera' | 'sucesso' | 'encerrada';

export interface Rodada {
  autor: string;
  minhas: string;
  deles: string;
  mensagem: string | null;
  feitaEm: string;
}

/** Uma negociação "traduzida" para quem está logado. */
export interface VisaoDaTroca {
  id: number;
  status: StatusNegociacao;
  souQuemPropos: boolean;
  outro: Referencia;
  /** As minhas férias hoje e com quais datas eu ficaria (pela proposta vigente). */
  minhasHoje: string | null;
  minhasNovas: string;
  /** As do outro, idem. */
  deleHoje: string | null;
  delesNovas: string;
  /** Os inícios da proposta vigente, do meu lado e do lado do outro (para preencher a contraproposta). */
  meuInicioProposto: string;
  inicioDoOutroProposto: string;
  minhaVez: boolean;
  possoCancelar: boolean;
  rotulo: string;
  tom: TomDoStatus;
  rodadas: Rodada[];
  decididoPor: string | null;
  mensagemDecisao: string | null;
}

export function visaoDaTroca(n: Negociacao, meuId: number): VisaoDaTroca {
  const souQuemPropos = n.solicitante.id === meuId;
  const outro = souQuemPropos ? n.colega : n.solicitante;
  const meuPedido = souQuemPropos ? n.pedidoDoSolicitante : n.pedidoDoColega;
  const pedidoDele = souQuemPropos ? n.pedidoDoColega : n.pedidoDoSolicitante;
  const vigente = n.propostas[n.propostas.length - 1];

  // Do lado de quem eu sou na negociação, quais campos da proposta são "meus".
  const meus = (p: typeof vigente) =>
    souQuemPropos ? intervalo(p.inicioSolicitante, p.fimSolicitante) : intervalo(p.inicioColega, p.fimColega);
  const deles = (p: typeof vigente) =>
    souQuemPropos ? intervalo(p.inicioColega, p.fimColega) : intervalo(p.inicioSolicitante, p.fimSolicitante);

  const minhaVez = n.vezDe?.id === meuId;
  return {
    id: n.id,
    status: n.status,
    souQuemPropos,
    outro,
    minhasHoje: meuPedido ? intervalo(meuPedido.inicio, meuPedido.fim) : null,
    minhasNovas: meus(vigente),
    deleHoje: pedidoDele ? intervalo(pedidoDele.inicio, pedidoDele.fim) : null,
    delesNovas: deles(vigente),
    meuInicioProposto: souQuemPropos ? vigente.inicioSolicitante : vigente.inicioColega,
    inicioDoOutroProposto: souQuemPropos ? vigente.inicioColega : vigente.inicioSolicitante,
    minhaVez,
    possoCancelar: souQuemPropos && emAberto(n.status),
    ...rotuloDoStatus(n.status, minhaVez, outro.nome),
    rodadas: n.propostas.map((p) => ({
      autor: p.autor.id === meuId ? 'Você' : p.autor.nome,
      minhas: meus(p),
      deles: deles(p),
      mensagem: p.mensagem,
      feitaEm: p.feitaEm,
    })),
    decididoPor: n.decididoPor ? (n.decididoPor.id === meuId ? 'Você' : n.decididoPor.nome) : null,
    mensagemDecisao: n.mensagemDecisao,
  };
}

export function emAberto(status: StatusNegociacao): boolean {
  return status === 'AGUARDANDO_COLEGA' || status === 'AGUARDANDO_SOLICITANTE' || status === 'AGUARDANDO_GESTOR';
}

function rotuloDoStatus(status: StatusNegociacao, minhaVez: boolean, outro: string): { rotulo: string; tom: TomDoStatus } {
  switch (status) {
    case 'AGUARDANDO_COLEGA':
    case 'AGUARDANDO_SOLICITANTE':
      return minhaVez ? { rotulo: 'Sua vez de responder', tom: 'acao' } : { rotulo: `Esperando ${outro}`, tom: 'espera' };
    case 'AGUARDANDO_GESTOR':
      return { rotulo: 'Com o gestor', tom: 'espera' };
    case 'APROVADA':
      return { rotulo: 'Aprovada', tom: 'sucesso' };
    case 'RECUSADA':
      return { rotulo: 'Recusada', tom: 'encerrada' };
    case 'CANCELADA':
      return { rotulo: 'Cancelada', tom: 'encerrada' };
  }
}

/** As em aberto primeiro (as que são a minha vez no topo), depois as encerradas. */
export function ordenar(visoes: VisaoDaTroca[]): VisaoDaTroca[] {
  const peso = (v: VisaoDaTroca) => (v.minhaVez ? 0 : emAberto(v.status) ? 1 : 2);
  return [...visoes].sort((a, b) => peso(a) - peso(b) || b.id - a.id);
}

/** Quantas trocas estão esperando a MINHA resposta (o número no menu). */
export function trocasEsperandoMim(negociacoes: Negociacao[], meuId: number): number {
  return negociacoes.filter((n) => n.vezDe?.id === meuId).length;
}

/** As minhas férias que posso oferecer numa troca: aprovadas e que ainda não começaram. */
export function feriasQuePossoOferecer(solicitacoes: Solicitacao[], hoje: string): Solicitacao[] {
  return solicitacoes
    .filter((s) => s.status === 'APROVADA' && s.inicio > hoje)
    .sort((a, b) => a.inicio.localeCompare(b.inicio));
}

/** A contraproposta digitada do MEU ponto de vista, no formato da API. */
export function paraContraproposta(
  souQuemPropos: boolean,
  meuInicio: string,
  inicioDoOutro: string,
  mensagem?: string,
): Contraproposta {
  return souQuemPropos
    ? { inicioSolicitante: meuInicio, inicioColega: inicioDoOutro, mensagem }
    : { inicioSolicitante: inicioDoOutro, inicioColega: meuInicio, mensagem };
}
