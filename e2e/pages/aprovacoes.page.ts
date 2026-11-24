import { Locator, Page } from '@playwright/test';

/**
 * PAGE OBJECT da tela Aprovações (gestor e RH). Ver o comentário de pages/solicitar.page.ts.
 *
 * A tela tem abas ("Pedidos", "Trocas", "Confirmação do RH", "Aprovadas"); o nome de cada aba
 * termina com a contagem ("Aprovadas · 3"), por isso a aba é achada pelo começo do nome.
 */
export class AprovacoesPagina {
  constructor(private readonly page: Page) {}

  /** Abre pelo menu, como uma pessoa faria. */
  async abrir(): Promise<void> {
    await this.page.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: /Aprovações/ }).click();
  }

  async abrirAba(nome: string): Promise<void> {
    await this.page
      .getByRole('navigation', { name: 'O que aprovar' })
      .getByRole('button', { name: new RegExp(`^${nome}`) })
      .click();
  }

  /** O cartão de umas férias na aba "Aprovadas": o nome da pessoa E as datas (pode haver outros). */
  cartaoDeFerias(nome: string, intervalo: string): Locator {
    return this.page.locator('.cartao').filter({ hasText: nome }).filter({ hasText: intervalo });
  }

  /** A mensagem de sucesso no topo ("Férias de Ana canceladas..."). */
  get mensagemDeSucesso(): Locator {
    return this.page.getByRole('status');
  }
}
