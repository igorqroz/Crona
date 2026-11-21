/*
 * As regras do calendário da tela Solicitar férias, em funções puras (testadas em
 * calendario.regras.spec.ts): montar os dias de um mês, contar quem está fora em cada dia,
 * a cor e o tamanho da "barrinha" e o que acontece quando a pessoa clica num dia.
 *
 * As datas circulam como texto ISO ("2027-03-17"): dá para comparar com < e >, e não sofre
 * com fuso horário.
 */
import { AusenciaNoCalendario } from '../../nucleo/modelos';

export interface Dia {
  iso: string;
  numero: number;
  fimDeSemana: boolean;
  feriado: boolean;
  passado: boolean;
  /** Os colegas do departamento fora neste dia. */
  fora: string[];
}

export interface Mes {
  titulo: string;
  /** Quantos quadrados vazios antes do dia 1 (o calendário começa no domingo). */
  vazios: number;
  dias: Dia[];
}

export interface Selecao {
  inicio: string | null;
  fim: string | null;
}

/** Uma data do JavaScript → "2027-03-17", no fuso do navegador. */
export function iso(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}

/** "março de 2027" → "Março 2027" */
function tituloDoMes(ano: number, mes: number): string {
  const texto = new Intl.DateTimeFormat('pt-BR', { month: 'long' }).format(new Date(ano, mes, 1));
  return `${texto.charAt(0).toUpperCase()}${texto.slice(1)} ${ano}`;
}

/** Os colegas fora num dia: quem tem férias que começam antes (ou no dia) e terminam depois (ou no dia). */
export function quemEstaFora(ausencias: AusenciaNoCalendario[], dia: string): string[] {
  return ausencias.filter((a) => a.inicio <= dia && a.fim >= dia).map((a) => a.nome);
}

/**
 * Monta um mês para o calendário.
 * @param mes 0 = janeiro (como no Date do JavaScript)
 */
export function montarMes(
  ano: number,
  mes: number,
  contexto: { hoje: string; feriados: string[]; ausencias: AusenciaNoCalendario[] },
): Mes {
  const feriados = new Set(contexto.feriados);
  const diasNoMes = new Date(ano, mes + 1, 0).getDate(); // dia 0 do mês seguinte = último dia deste
  const dias: Dia[] = [];
  for (let numero = 1; numero <= diasNoMes; numero++) {
    const data = new Date(ano, mes, numero);
    const dia = iso(data);
    dias.push({
      iso: dia,
      numero,
      fimDeSemana: data.getDay() === 0 || data.getDay() === 6,
      feriado: feriados.has(dia),
      passado: dia <= contexto.hoje, // hoje também não: férias começam no mínimo amanhã
      fora: quemEstaFora(contexto.ausencias, dia),
    });
  }
  return { titulo: tituloDoMes(ano, mes), vazios: new Date(ano, mes, 1).getDay(), dias };
}

export type TomDaBarra = 'nenhum' | 'um' | 'varios' | 'limite';

/**
 * A "barrinha" embaixo de cada dia: cresce com o número de colegas fora e fica laranja quando
 * mais uma pessoa (eu) passaria do limite do departamento.
 */
export function barra(fora: number, limite: number | null): { largura: number; tom: TomDaBarra } {
  const largura = Math.min(100, fora === 0 ? 0 : 4 + 24 * fora);
  if (fora === 0) return { largura, tom: 'nenhum' };
  if (limite !== null && fora >= limite) return { largura, tom: 'limite' };
  return { largura, tom: fora === 1 ? 'um' : 'varios' };
}

/**
 * O clique num dia:
 * - sem início (ou com o intervalo já completo): este dia vira o novo início;
 * - com início e sem fim: este dia vira o fim (ou o novo início, se for antes do início).
 */
export function proximaSelecao(atual: Selecao, dia: string): Selecao {
  if (!atual.inicio || atual.fim) {
    return { inicio: dia, fim: null };
  }
  return dia < atual.inicio ? { inicio: dia, fim: null } : { inicio: atual.inicio, fim: dia };
}

/** Quantos dias de "2027-03-17" a "2027-03-31", contando os dois: 15. */
export function diasEntre(inicio: string, fim: string): number {
  const [a1, m1, d1] = inicio.split('-').map(Number);
  const [a2, m2, d2] = fim.split('-').map(Number);
  return Math.round((Date.UTC(a2, m2 - 1, d2) - Date.UTC(a1, m1 - 1, d1)) / 86_400_000) + 1;
}
