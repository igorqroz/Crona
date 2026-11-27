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
  /** Só quando o RH cancelou férias que já estavam aprovadas. */
  canceladoPor?: Referencia | null;
  motivoCancelamento?: string | null;
}

/** Férias aprovadas que ainda não começaram: a aba "Aprovadas" do RH, onde dá para cancelar. */
export interface FeriasAprovada {
  id: number;
  colaborador: Referencia;
  departamento: string | null;
  inicio: string;
  fim: string;
  diasGozo: number;
  diasAbono: number;
  aprovadoPor: Referencia | null;
  /** Por que não dá para cancelar agora (são as férias de quem está logado, troca em aberto). Nulo = pode. */
  bloqueio: string | null;
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

/** POST /minhas-ferias/negociacoes. Os nomes são do ponto de vista da negociação (solicitante = quem propõe). */
export interface PropostaDeTroca {
  pedidoDoColegaId: number;
  pedidoDoSolicitanteId?: number;
  inicioSolicitante: string;
  /** Só quando quem propõe não tem férias marcadas. */
  diasSolicitante?: number;
  inicioColega: string;
  mensagem?: string;
}

export interface Contraproposta {
  inicioSolicitante: string;
  inicioColega: string;
  mensagem?: string;
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

// ------------------------------------------------------------------ gestor e RH (Fase 7d)

export type SituacaoDoPedido = 'SEM_CONFLITO' | 'COM_ATENCAO' | 'COM_IMPEDIMENTO';

/** Um pedido na fila de aprovação (GET /aprovacoes e /confirmacoes-rh). */
export interface ItemDeAprovacao {
  id: number;
  colaborador: Referencia;
  departamento: string | null;
  inicio: string;
  fim: string;
  diasGozo: number;
  diasAbono: number;
  periodo: number;
  recado: string | null;
  enviadoEm: string;
  situacao: SituacaoDoPedido;
  /** Só as verificações que não passaram. */
  alertas: Verificacao[];
}

export interface DetalheDaAprovacao {
  pedido: ItemDeAprovacao;
  livresNoPeriodo: number;
  voltaAoTrabalho: string;
  concederAte: string;
  limiteDoDepartamento: number | null;
  verificacoes: Verificacao[];
  colegasNoPeriodo: AusenciaNoCalendario[];
}

export interface TrocaParaAprovar {
  negociacao: Negociacao;
  situacao: SituacaoDoPedido;
  verificacoesSolicitante: Verificacao[];
  verificacoesColega: Verificacao[];
}

/** A decisão do gestor. Para recusar e para "aprovar mesmo assim", a mensagem é obrigatória. */
export interface Decisao {
  mesmoAssim?: boolean;
  mensagem?: string;
}

// ------------------------------------------------------------------ calendário da equipe (Fase 8c)

export interface CalendarioDaGestao {
  de: string;
  ate: string;
  departamentos: {
    id: number | null;
    nome: string;
    ativos: number;
    limite: number | null;
    pessoas: { id: number; nome: string; ausencias: { pedidoId: number; inicio: string; fim: string; pendente: boolean }[] }[];
  }[];
  restricoes: { motivo: string; departamentoId: number | null; inicio: string; fim: string; bloqueia: boolean }[];
  feriados: string[];
}

// ------------------------------------------------------------------ notificações (Fase 8b)

export interface Notificacao {
  id: number;
  tipo: string;
  mensagem: string;
  /** A tela do Crona que a notificação abre (ex.: "/aprovacoes"). */
  link: string | null;
  lida: boolean;
  criadaEm: string;
}

export interface Notificacoes {
  naoLidas: number;
  itens: Notificacao[];
}

/** O corpo dos erros da API (ProblemDetail, RFC 9457). */
export interface Problema {
  title?: string;
  status?: number;
  detail?: string;
  /** Só nos 422 de pedido com impedimento: o checklist das regras. */
  verificacoes?: Verificacao[];
  /** Só nos 400 de validação: o erro de cada campo ({ nome: "Informe o nome do departamento." }). */
  erros?: Record<string, string>;
}

// ------------------------------------------------------------------ cadastros (RH)

/** GET /departamentos: um departamento com quantas pessoas ativas tem e o limite da EMP-01. */
export interface DepartamentoResumo {
  id: number;
  nome: string;
  pessoas: number;
  /** Quantas podem estar de férias no mesmo dia. Nulo = a regra está desligada na empresa. */
  limiteFora: number | null;
}
