import { expect, test } from '@playwright/test';

import { feriasAprovadasPelaApi } from '../fixtures/api';
import { ANA, RAFAEL, ROBERTO } from '../fixtures/contas';
import { AprovacoesPagina } from '../pages/aprovacoes.page';
import { LoginPagina } from '../pages/login.page';

/** "2027-01-11" → "11/01" (o formato das datas nas telas). */
const diaMes = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

/**
 * O RH cancela férias que já estavam aprovadas (regras-de-negocio.md, seção 4).
 *
 * A preparação (Ana pede, Roberto aprova) é feita pela API: o que este teste prova é o
 * cancelamento pela tela e o que acontece depois, com duas pessoas em navegadores separados.
 *
 * As datas são procuradas a partir de ~6 meses para frente, longe das do ferias.spec.ts
 * (que roda em paralelo com a mesma Ana): assim os dois testes não escolhem as mesmas datas.
 */
test('Rafael (RH) cancela as férias aprovadas da Ana, e ela é avisada com o motivo', async ({ browser, request }) => {
  const ferias = await feriasAprovadasPelaApi(request, ANA, ROBERTO, 10, 180);
  const intervaloNaTela = `${diaMes(ferias.inicio)} – ${diaMes(ferias.fim)}`; // "11/01 – 20/01"
  const MOTIVO = 'Teste E2E: aprovado por engano';

  const rafael = await (await browser.newContext()).newPage();
  const ana = await (await browser.newContext()).newPage();

  await test.step('Rafael acha as férias da Ana na aba Aprovadas e cancela com o motivo', async () => {
    await new LoginPagina(rafael).entrar(RAFAEL);
    const aprovacoes = new AprovacoesPagina(rafael);
    await aprovacoes.abrir();
    await aprovacoes.abrirAba('Aprovadas');

    const cartao = aprovacoes.cartaoDeFerias(ANA.nome, intervaloNaTela);
    await expect(cartao).toHaveCount(1);
    await cartao.getByRole('button', { name: 'Cancelar férias' }).click();

    // Sem motivo, a tela não deixa (e nem chama a API).
    await cartao.getByRole('button', { name: 'Cancelar as férias de Ana' }).click();
    await expect(cartao.getByRole('alert')).toContainText('Escreva o motivo do cancelamento.');

    await cartao.getByRole('textbox', { name: 'Motivo do cancelamento' }).fill(MOTIVO);
    await cartao.getByRole('button', { name: 'Cancelar as férias de Ana' }).click();

    await expect(aprovacoes.mensagemDeSucesso).toContainText('Férias de Ana canceladas');
    await expect(cartao).toHaveCount(0); // saiu da lista
  });

  await test.step('Ana recebe a notificação com o motivo', async () => {
    await new LoginPagina(ana).entrar(ANA);
    await ana.getByRole('button', { name: /^Notificações/ }).click();

    const painel = ana.getByRole('dialog', { name: 'Notificações' });
    await expect(painel).toContainText(
      `${RAFAEL.nome} cancelou suas férias de ${diaMes(ferias.inicio)} a ${diaMes(ferias.fim)}`,
    );
    await expect(painel).toContainText(MOTIVO);
  });

  await test.step('o aviso em PDF dessas férias some do Início', async () => {
    await ana.keyboard.press('Escape');
    const avisos = ana.getByRole('region', { name: 'Avisos de férias' });
    await expect(avisos).toBeVisible();
    await expect(avisos).not.toContainText(`${diaMes(ferias.inicio)} – ${diaMes(ferias.fim)}`);
  });
});
