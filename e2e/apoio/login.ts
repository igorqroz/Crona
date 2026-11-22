import { expect, Page } from '@playwright/test';

import { Conta, primeiroNome } from './contas';

/**
 * Entra no Crona pela tela de login, como uma pessoa faria: digita o e-mail e a senha e
 * clica em "Entrar". Termina só quando o Início aparece ("Olá, Ana").
 *
 * Os campos são achados pelo RÓTULO ("E-mail", "Senha"), e não por id ou classe CSS:
 * é assim que o usuário (e o leitor de tela) enxerga a página. Se alguém mudar o CSS,
 * o teste continua passando; se alguém tirar o rótulo, o teste falha (e devia mesmo).
 */
export async function entrar(page: Page, conta: Conta): Promise<void> {
  await page.goto('/login');
  await page.getByLabel('E-mail').fill(conta.email);
  await page.getByLabel('Senha').fill(conta.senha);
  await page.getByRole('button', { name: 'Entrar', exact: true }).click();

  await expect(page).toHaveURL(/\/inicio$/);
  await expect(page.getByRole('heading', { name: `Olá, ${primeiroNome(conta)}` })).toBeVisible();
}
