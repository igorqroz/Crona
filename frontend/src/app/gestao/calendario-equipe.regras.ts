/*
 * As regras do Calendário da equipe (Fase 8c), em funções puras (calendario-equipe.regras.spec.ts).
 * Transformam a resposta da API numa grade: uma coluna por dia, uma linha por pessoa.
 */
import { CalendarioDaGestao } from '../nucleo/modelos';
import { diasEntre, iso } from '../paginas/solicitar/calendario.regras';

/** As opções de janela do redesign: 2 semanas, 6 semanas ou um trimestre (13 semanas). */
export type Janela = 2 | 6 | 13;

const MESES = ['jan', 'fev', 'mar', 'abr', 'mai', 'jun', 'jul', 'ago', 'set', 'out', 'nov', 'dez'];

/** A segunda-feira da semana de uma data (o calendário sempre começa numa segunda). */
export function segundaDaSemana(data: Date): string {
  const dia = new Date(data.getFullYear(), data.getMonth(), data.getDate());
  const deslocamento = (dia.getDay() + 6) % 7; // segunda = 0 ... domingo = 6
  dia.setDate(dia.getDate() - deslocamento);
  return iso(dia);
}

export function somarDias(dataIso: string, dias: number): string {
  const [a, m, d] = dataIso.split('-').map(Number);
  return iso(new Date(a, m - 1, d + dias));
}

export interface DiaDaGrade {
  iso: string;
  fimDeSemana: boolean;
  feriado: boolean;
  /** Só nas segundas: "10 fev" (o cabeçalho mostra uma etiqueta por semana). */
  semana: string | null;
}

export interface Barra {
  de: number;
  ate: number;
  pendente: boolean;
  /** O texto dentro da barra: "10–21/02" ou "24/02–10/03". */
  rotulo: string;
}

export interface Faixa {
  de: number;
  ate: number;
  motivo: string;
  bloqueia: boolean;
}

export interface BlocoDoDepartamento {
  nome: string;
  subtitulo: string;
  /** Os dias (colunas) em que há mais gente fora do que o limite, e quantos. */
  acima: { coluna: number; total: number }[];
  /** Os períodos restritos que valem para este departamento (dele ou da empresa toda). */
  restricoes: Faixa[];
  pessoas: { nome: string; barras: Barra[] }[];
}

export interface Grade {
  dias: DiaDaGrade[];
  /** A coluna de hoje, ou null se hoje está fora da janela. */
  hoje: number | null;
  departamentos: BlocoDoDepartamento[];
}

/** "2027-02-10" e "2027-02-21" → "10–21/02"; meses diferentes → "24/02–10/03". */
export function rotulo(inicio: string, fim: string): string {
  const [, mi, di] = inicio.split('-');
  const [, mf, df] = fim.split('-');
  return mi === mf ? `${di}–${df}/${mf}` : `${di}/${mi}–${df}/${mf}`;
}

export function montarGrade(dados: CalendarioDaGestao, hoje: string): Grade {
  const total = diasEntre(dados.de, dados.ate);
  const feriados = new Set(dados.feriados);
  const dias: DiaDaGrade[] = Array.from({ length: total }, (_, i) => {
    const dia = somarDias(dados.de, i);
    const [a, m, d] = dia.split('-').map(Number);
    const semana = new Date(a, m - 1, d).getDay();
    return {
      iso: dia,
      fimDeSemana: semana === 0 || semana === 6,
      feriado: feriados.has(dia),
      semana: semana === 1 ? `${d} ${MESES[m - 1]}` : null,
    };
  });

  // A coluna de uma data, cortada para caber na janela.
  const coluna = (data: string) => Math.min(total - 1, Math.max(0, diasEntre(dados.de, data) - 1));
  const dentro = (inicio: string, fim: string) => inicio <= dados.ate && fim >= dados.de;

  const departamentos = dados.departamentos.map((dep) => {
    const pessoas = dep.pessoas.map((p) => ({
      nome: p.nome,
      barras: p.ausencias
        .filter((a) => dentro(a.inicio, a.fim))
        .map((a) => ({ de: coluna(a.inicio), ate: coluna(a.fim), pendente: a.pendente, rotulo: rotulo(a.inicio, a.fim) })),
    }));

    // Quantas PESSOAS (não pedidos) estão fora em cada dia; acima do limite vira alerta.
    const acima: { coluna: number; total: number }[] = [];
    if (dep.limite !== null) {
      for (let i = 0; i < total; i++) {
        const fora = pessoas.filter((p) => p.barras.some((b) => b.de <= i && b.ate >= i)).length;
        if (fora > dep.limite) {
          acima.push({ coluna: i, total: fora });
        }
      }
    }

    const restricoes = dados.restricoes
      .filter((r) => (r.departamentoId === null || r.departamentoId === dep.id) && dentro(r.inicio, r.fim))
      .map((r) => ({ de: coluna(r.inicio), ate: coluna(r.fim), motivo: r.motivo, bloqueia: r.bloqueia }));

    const quantos = `${dep.ativos} ${dep.ativos === 1 ? 'pessoa' : 'pessoas'}`;
    return {
      nome: dep.nome,
      subtitulo: dep.limite === null ? quantos : `${quantos} · limite ${dep.limite}`,
      acima,
      restricoes,
      pessoas,
    };
  });

  const colunaDeHoje = hoje >= dados.de && hoje <= dados.ate ? diasEntre(dados.de, hoje) - 1 : null;
  return { dias, hoje: colunaDeHoje, departamentos };
}
