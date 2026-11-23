import { expect, Locator, Page } from '@playwright/test';

import { Conta, primeiroNome } from '../fixtures/contas';

/**
 * PAGE OBJECT da tela de login (ver o comentário de pages/solicitar.page.ts).
 *
 * Os campos são achados pelo RÓTULO ("E-mail", "Senha"), e não por id ou classe CSS:
 * é assim que o usuário (e o leitor de tela) enxerga a página. Se alguém mudar o CSS,
 * o teste continua passando; se alguém tirar o rótulo, o teste falha (e devia mesmo).
 */
export class LoginPagina {
  readonly email: Locator;
  readonly senha: Locator;
  readonly botaoEntrar: Locator;

  constructor(private readonly page: Page) {
    this.email = page.getByLabel('E-mail');
    this.senha = page.getByLabel('Senha');
    // exact: só o botão cujo nome é exatamente "Entrar" (a tela também tem os botões das contas de exemplo).
    this.botaoEntrar = page.getByRole('button', { name: 'Entrar', exact: true });
  }

  async abrir(): Promise<void> {
    await this.page.goto('/login');
  }

  /** Abre a tela, digita e clica em "Entrar". Não confere o resultado (serve também para o login que falha). */
  async tentarEntrar(email: string, senha: string): Promise<void> {
    await this.abrir();
    await this.email.fill(email);
    await this.senha.fill(senha);
    await this.botaoEntrar.click();
  }

  /**
   * Entra como uma pessoa faria e só termina quando o Início aparece ("Olá, Ana").
   * É o passo inicial de quase todos os testes.
   */
  async entrar(conta: Conta): Promise<void> {
    await this.tentarEntrar(conta.email, conta.senha);

    await expect(this.page).toHaveURL(/\/inicio$/);
    await expect(this.page.getByRole('heading', { name: `Olá, ${primeiroNome(conta)}` })).toBeVisible();
  }
}
