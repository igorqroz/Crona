import { expect, test } from '@playwright/test';

import { proximasFeriasValidas } from '../fixtures/api';
import { ANA, ROBERTO } from '../fixtures/contas';
import { LoginPagina } from '../pages/login.page';
import { SolicitarPagina } from '../pages/solicitar.page';

/** "2027-01-11" → "11/01" (o formato das datas nas telas). */
const diaMes = (iso: string) => `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;

/**
 * O fluxo principal do Crona, de ponta a ponta, com DUAS pessoas ao mesmo tempo:
 * a Ana pede férias e o Roberto (gestor dela) aprova.
 *
 * Cada pessoa tem o seu "contexto" do navegador: é como abrir duas janelas anônimas,
 * cada uma com o seu login. As etapas (test.step) aparecem separadas no relatório.
 *
 * Este teste MUDA o banco (deixa um pedido aprovado). Ele escolhe datas livres a cada
 * execução, então pode rodar algumas vezes seguidas; para começar do zero, recrie o banco
 * (ver e2e/README.md).
 */
test('Ana pede 15 dias de férias, Roberto aprova e Ana vê aprovado', async ({ browser, request }) => {
  const DIAS = 15;
  const { inicio, fim } = await proximasFeriasValidas(request, ANA, DIAS);
  const intervalo = `${diaMes(inicio)} – ${diaMes(fim)}`; // "11/01 – 25/01"

  const ana = await (await browser.newContext()).newPage();
  const roberto = await (await browser.newContext()).newPage();

  await test.step(`Ana escolhe de ${inicio} a ${fim} no calendário`, async () => {
    await new LoginPagina(ana).entrar(ANA);
    const solicitar = new SolicitarPagina(ana);
    await solicitar.abrir();
    await solicitar.escolherPeriodo(inicio, fim);

    // A tela pede a simulação à API e mostra o resumo e o checklist das regras.
    await expect(solicitar.resumo).toContainText(new RegExp(`Duração\\s*${DIAS} dias`));
    await expect(solicitar.botaoEnviar(ROBERTO.nome)).toBeEnabled();
  });

  await test.step('Ana envia com um recado e acompanha no Início', async () => {
    const solicitar = new SolicitarPagina(ana);
    await solicitar.resumo.getByLabel('Recado para o gestor (opcional)').fill('Teste E2E: viagem marcada');
    await solicitar.botaoEnviar(ROBERTO.nome).click();

    await expect(ana).toHaveURL(/\/inicio$/);
    const cartao = ana.getByRole('region', { name: 'Seu pedido' });
    await expect(cartao).toContainText(intervalo);
    await expect(cartao).toContainText('Com o seu gestor');
  });

  await test.step('Roberto acha o pedido da Ana nas Aprovações e aprova', async () => {
    await new LoginPagina(roberto).entrar(ROBERTO);
    await roberto.getByRole('navigation', { name: 'Principal' }).getByRole('link', { name: /Aprovações/ }).click();

    // A tela já abre com o 1º pedido escolhido: espera esse detalhe carregar.
    const detalhe = roberto.getByRole('region', { name: 'Detalhe do pedido' });
    await expect(detalhe.getByRole('heading', { level: 2 })).toBeVisible();

    // O item da lista com o nome da Ana E as datas deste teste (pode haver outros pedidos).
    // É um só: a API não aceita o mesmo período pedido duas vezes pela mesma pessoa (CRONA-01).
    const item = roberto
      .getByRole('region', { name: 'Pedidos' })
      .getByRole('button')
      .filter({ hasText: ANA.nome })
      .filter({ hasText: intervalo });

    // Clicar em OUTRO pedido busca o detalhe na API (GET /api/aprovacoes/{id}) e, quando ele
    // chega, a tela limpa a mensagem. Se o teste digitar antes da resposta, o texto some (foi
    // assim que este teste ficou instável na primeira versão). Então: clica E espera a resposta.
    const detalheChegou = roberto.waitForResponse(
      (r) => r.request().method() === 'GET' && /\/api\/aprovacoes\/\d+$/.test(new URL(r.url()).pathname),
    );
    await item.click();
    await detalheChegou;

    await expect(detalhe).toContainText('Teste E2E: viagem marcada'); // o recado chegou

    // A mensagem para a Ana. Ela é opcional num pedido sem alerta, mas OBRIGATÓRIA quando o
    // pedido tem alerta (ex.: período vencido, que paga em dobro): aí o botão vira
    // "Aprovar mesmo assim" e o motivo fica registrado. Escrevendo sempre, o teste serve
    // para os dois casos (as datas mudam conforme o dia em que o teste roda).
    await detalhe.getByRole('textbox', { name: /^Mensagem para Ana/ }).fill('Aprovado pelo teste E2E. Boas férias!');
    await detalhe.getByRole('button', { name: /^Aprovar/ }).click();

    await expect(roberto.getByRole('status')).toContainText('aprovado');
  });

  await test.step('Ana recebe a notificação da aprovação no sino', async () => {
    await ana.reload();
    await ana.getByRole('button', { name: /^Notificações/ }).click();

    // O texto da notificação traz as datas: assim temos certeza de que é ESTE pedido.
    const painel = ana.getByRole('dialog', { name: 'Notificações' });
    await expect(painel).toContainText(`${ROBERTO.nome} aprovou suas férias de ${diaMes(inicio)} a ${diaMes(fim)}`);
  });
});
