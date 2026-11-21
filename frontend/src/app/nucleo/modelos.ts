/*
 * Os formatos das respostas da API, em TypeScript.
 *
 * Cada interface espelha um record Java do back-end (ex.: PeriodoAquisitivoResposta.java).
 * Datas sem hora (LocalDate) chegam como texto "2026-11-21"; com hora (Instant), como
 * "2026-11-21T13:00:00Z". Por isso o tipo delas é string.
 */

export type Perfil = 'COLABORADOR' | 'GESTOR' | 'ADMIN';

export interface Referencia {
  id: number;
  nome: string;
}

export interface Usuario {
  id: number;
  nome: string;
  perfil: Perfil;
}

export interface LoginResposta {
  token: string;
  tipo: string;
  expiraEm: string;
  usuario: Usuario;
}

export type SituacaoPeriodo = 'EM_AQUISICAO' | 'DISPONIVEL' | 'A_VENCER' | 'VENCIDO' | 'ESGOTADO';

export interface PeriodoAquisitivo {
  numero: number;
  inicio: string;
  fim: string;
  tirarAte: string;
  faltasInjustificadas: number;
  direito: number;
  aprovados: number;
  emPedido: number;
  livres: number;
  situacao: SituacaoPeriodo;
}

export type StatusSolicitacao = 'AGUARDANDO_GESTOR' | 'AGUARDANDO_RH' | 'APROVADA' | 'RECUSADA' | 'CANCELADA';

export interface Solicitacao {
  id: number;
  status: StatusSolicitacao;
  inicio: string;
  fim: string;
  diasGozo: number;
  diasAbono: number;
  periodo: number;
  recado: string | null;
  enviadoEm: string;
  decididoPor: Referencia | null;
  mensagemDecisao: string | null;
}

export type StatusNegociacao =
  | 'AGUARDANDO_COLEGA'
  | 'AGUARDANDO_SOLICITANTE'
  | 'AGUARDANDO_GESTOR'
  | 'APROVADA'
  | 'RECUSADA'
  | 'CANCELADA';

export interface FeriasResumo {
  id: number;
  inicio: string;
  fim: string;
  diasGozo: number;
}

export interface Proposta {
  autor: Referencia;
  inicioSolicitante: string;
  fimSolicitante: string;
  inicioColega: string;
  fimColega: string;
  mensagem: string | null;
  feitaEm: string;
}

export interface Negociacao {
  id: number;
  status: StatusNegociacao;
  solicitante: Referencia;
  colega: Referencia;
  vezDe: Referencia | null;
  pedidoDoColega: FeriasResumo;
  pedidoDoSolicitante: FeriasResumo | null;
  propostas: Proposta[];
  decididoPor: Referencia | null;
  mensagemDecisao: string | null;
  criadaEm: string;
}

export interface FeriasDoColega {
  pedidoId: number;
  colaborador: Referencia;
  inicio: string;
  fim: string;
  diasGozo: number;
}

/** Uma linha do checklist das regras (CLT-03, EMP-01...). */
export interface Verificacao {
  regra: string;
  resultado: 'OK' | 'ATENCAO' | 'IMPEDIMENTO';
  mensagem: string;
}

/** O pedido que a tela Solicitar monta (o mesmo corpo serve para simular e para enviar). */
export interface PedidoDeFerias {
  inicio: string;
  diasGozo: number;
  diasAbono: number;
  recado?: string;
}

export interface Simulacao {
  inicio: string;
  fim: string;
  voltaAoTrabalho: string;
  periodo: number;
  livresNoPeriodo: number;
  podeEnviar: boolean;
  resumo: string;
  verificacoes: Verificacao[];
  colegasNoPeriodo: { nome: string; inicio: string; fim: string; pendente: boolean }[];
}

export interface AusenciaNoCalendario {
  nome: string;
  inicio: string;
  fim: string;
  pendente: boolean;
}

export interface CalendarioDaEquipe {
  departamento: string | null;
  limite: number | null;
  gestor: Referencia | null;
  ausencias: AusenciaNoCalendario[];
  feriados: string[];
}

/** O corpo dos erros da API (ProblemDetail, RFC 9457). */
export interface Problema {
  title?: string;
  status?: number;
  detail?: string;
  /** Só nos 422 de pedido com impedimento: o checklist das regras. */
  verificacoes?: Verificacao[];
}
