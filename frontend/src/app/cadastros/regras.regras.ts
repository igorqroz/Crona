import { DepartamentoResumo, RegrasDaEmpresa } from '../nucleo/modelos';

/*
 * As contas e a validação da tela de Regras de férias (Cadastros 3), sem Angular.
 */

/**
 * EMP-01: quantas pessoas do departamento podem estar fora no mesmo dia.
 * A MESMA conta do ValidadorDaEquipe.limiteDoDepartamento (back-end), para a prévia nunca discordar:
 * 10 pessoas e 70% trabalhando → 3 fora; nunca menos que 1.
 */
export function limiteDoDepartamento(pessoas: number, percentualTrabalhando: number): number {
  return Math.max(1, Math.floor((pessoas * (100 - percentualTrabalhando)) / 100));
}

/** "Engenharia · 5 pessoas → até 1 fora por dia" (só os departamentos com gente). */
export function previaDosDepartamentos(departamentos: DepartamentoResumo[], percentual: number): string[] {
  return departamentos
    .filter((d) => d.pessoas > 0)
    .map((d) => {
      const pessoas = d.pessoas === 1 ? '1 pessoa' : `${d.pessoas} pessoas`;
      return `${d.nome} · ${pessoas} → até ${limiteDoDepartamento(d.pessoas, percentual)} fora por dia`;
    });
}

/** "Comercial, Financeiro e Suporte ainda não têm ninguém." (null se todos têm gente). */
export function departamentosVazios(departamentos: DepartamentoResumo[]): string | null {
  const nomes = departamentos.filter((d) => d.pessoas === 0).map((d) => d.nome);
  if (nomes.length === 0) {
    return null;
  }
  const lista = nomes.length === 1 ? nomes[0] : `${nomes.slice(0, -1).join(', ')} e ${nomes[nomes.length - 1]}`;
  return nomes.length === 1 ? `${lista} ainda não tem ninguém.` : `${lista} ainda não têm ninguém.`;
}

/** EMP-03: a data mais cedo em que pode começar um pedido feito hoje ("27/12/2026"). */
export function primeiroDiaPossivel(antecedenciaDias: number, hoje: Date = new Date()): string {
  const data = new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate() + antecedenciaDias);
  return data.toLocaleDateString('pt-BR');
}

export type ErrosDasRegras = Partial<Record<'antecedencia' | 'percentual' | 'limite', string>>;

/** As mesmas regras da API (RegrasDaEmpresa.java). Vazio = pode salvar. */
export function validarRegras(regras: RegrasDaEmpresa): ErrosDasRegras {
  const erros: ErrosDasRegras = {};
  const { antecedenciaMinimaDias: dias, percentualMinimoTrabalhando: pct, limiteFeriasSimultaneasEmpresa: limite } = regras;
  if (!Number.isInteger(dias) || dias < 30) {
    erros.antecedencia = 'A antecedência mínima é de 30 dias.';
  } else if (dias > 365) {
    erros.antecedencia = 'A antecedência pode ser de no máximo 365 dias.';
  }
  if (pct !== null && (!Number.isInteger(pct) || pct < 0 || pct > 100)) {
    erros.percentual = 'Informe um percentual de 0 a 100.';
  }
  if (limite !== null && (!Number.isInteger(limite) || limite < 1)) {
    erros.limite = 'Informe pelo menos 1 pessoa.';
  }
  return erros;
}

/** As regras mudaram em relação às salvas? (a barra "Alterações não salvas" depende disso). */
export function mudou(atual: RegrasDaEmpresa, salvas: RegrasDaEmpresa): boolean {
  return (
    atual.antecedenciaMinimaDias !== salvas.antecedenciaMinimaDias ||
    atual.percentualMinimoTrabalhando !== salvas.percentualMinimoTrabalhando ||
    atual.limiteFeriasSimultaneasEmpresa !== salvas.limiteFeriasSimultaneasEmpresa ||
    atual.exigeConfirmacaoRh !== salvas.exigeConfirmacaoRh
  );
}
