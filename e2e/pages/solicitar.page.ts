import { expect, Locator, Page } from '@playwright/test';

/**
 * PAGE OBJECT da tela "Solicitar férias".
 *
 * Page Object é um padrão clássico de automação: uma classe que sabe COMO mexer numa tela
 * (quais botões existem, como navegar no calendário). O teste só diz O QUE quer fazer:
 *
 *     await solicitar.escolherPeriodo('2027-01-11', '2027-01-25');
 *
 * Se a tela mudar, o conserto fica num lugar só (aqui), e não em todos os testes.
 */
export class SolicitarPagina {
  readonly resumo: Locator;
  private readonly proximoMes: Locator;

  /** O primeiro dos 2 meses que o calendário mostra (o calendário abre no mês atual). */
  private primeiroMesNaTela: { ano: number; mes: number };

  constructor(private readonly page: Page) {
    this.resumo = page.getByRole('region', { name: 'Resumo' });
    this.proximoMes = page.getByRole('button', { name: 'Próximo mês' });
    const agora = new Date();
    this.primeiroMesNaTela = { ano: agora.getFullYear(), mes: agora.getMonth() };
  }

  async abrir(): Promise<void> {
    await this.page.goto('/solicitar');
    await expect(this.page.getByRole('heading', { name: 'Solicitar férias' })).toBeVisible();
  }

  /** Clica no primeiro e no último dia, como a pessoa faz. */
  async escolherPeriodo(inicio: string, fim: string): Promise<void> {
    await this.clicarNoDia(inicio);
    await this.clicarNoDia(fim);
  }

  /** O botão "Enviar para Roberto Neves" (o nome do gestor vem da API). */
  botaoEnviar(gestor: string): Locator {
    return this.resumo.getByRole('button', { name: `Enviar para ${gestor}` });
  }

  /**
   * Avança o calendário até o mês do dia (a tela mostra 2 meses por vez) e clica nele.
   *
   * Cada dia é um <button> com um aria-label para leitores de tela, por exemplo
   * "11 de janeiro, segunda, ninguém da equipe fora". O teste acha o botão por esse texto.
   */
  private async clicarNoDia(dataIso: string): Promise<void> {
    const [ano, mes, dia] = dataIso.split('-').map(Number);
    const alvo = { ano, mes: mes - 1 };

    // Quantos meses o dia está à frente do 1º mês da tela? 0 ou 1: já está visível.
    let distancia = mesesEntre(this.primeiroMesNaTela, alvo);
    while (distancia > 1) {
      await this.proximoMes.click();
      this.primeiroMesNaTela = somarMes(this.primeiroMesNaTela, 1);
      distancia--;
    }
    // Espera o mês aparecer (ex.: "Janeiro 2027") antes de procurar o dia.
    await expect(this.page.getByRole('heading', { name: tituloDoMes(alvo), exact: true })).toBeVisible();

    const nomeDoMes = tituloDoMes(alvo).split(' ')[0].toLowerCase();
    await this.page.getByRole('button', { name: new RegExp(`^${dia} de ${nomeDoMes},`) }).click();
  }
}

// ------------------------------------------------------------------ meses

type Mes = { ano: number; mes: number }; // mes: 0 = janeiro (como no Date do JavaScript)

function mesesEntre(de: Mes, ate: Mes): number {
  return (ate.ano - de.ano) * 12 + (ate.mes - de.mes);
}

function somarMes(m: Mes, quantos: number): Mes {
  const data = new Date(m.ano, m.mes + quantos, 1);
  return { ano: data.getFullYear(), mes: data.getMonth() };
}

/** { ano: 2027, mes: 0 } → "Janeiro 2027" (igual ao título do mês na tela). */
function tituloDoMes(m: Mes): string {
  const nome = new Intl.DateTimeFormat('pt-BR', { month: 'long' }).format(new Date(m.ano, m.mes, 1));
  return `${nome.charAt(0).toUpperCase()}${nome.slice(1)} ${m.ano}`;
}
