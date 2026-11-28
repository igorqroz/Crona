import { expect, test } from '@playwright/test';

import { Conta, RAFAEL } from '../fixtures/contas';
import { ColaboradoresPagina } from '../pages/colaboradores.page';
import { DepartamentosPagina } from '../pages/departamentos.page';
import { LoginPagina } from '../pages/login.page';

/**
 * Cadastros do RH, de ponta a ponta: um departamento novo, uma pessoa nova nele, o primeiro
 * acesso dela, e o RH desativando esse acesso.
 *
 * Repetível: o departamento e a pessoa ganham um sufixo único a cada execução, então o teste não
 * esbarra no que uma execução anterior deixou no banco. O departamento fica (com a pessoa inativa
 * dentro, o banco não deixa excluir); é só um nome a mais na lista.
 */
test('Rafael (RH) cria um departamento, cadastra uma pessoa nele, ela entra, e o RH desativa o acesso', async ({ browser }) => {
  const sufixo = Date.now().toString(36);
  const DEPARTAMENTO = `E2E ${sufixo}`;
  const pessoa = {
    nome: `Teste ${sufixo} Silva`,
    email: `teste.${sufixo}@e2e.crona`,
    senha: 'senha-do-e2e',
    departamento: DEPARTAMENTO,
    admissao: '2024-01-08',
  };
  const contaDaPessoa: Conta = { nome: pessoa.nome, email: pessoa.email, senha: pessoa.senha };
  const primeiro = `Teste`;

  const rafael = await (await browser.newContext()).newPage();
  const departamentos = new DepartamentosPagina(rafael);
  const colaboradores = new ColaboradoresPagina(rafael);

  await test.step('Rafael cria o departamento: ele aparece vazio na lista', async () => {
    await new LoginPagina(rafael).entrar(RAFAEL);
    await departamentos.abrir();
    await departamentos.criar(DEPARTAMENTO);

    await expect(departamentos.mensagemDeSucesso).toHaveText(`Departamento ${DEPARTAMENTO} criado.`);
    await expect(departamentos.linha(DEPARTAMENTO)).toContainText('Ninguém ainda');
  });

  await test.step('Rafael cadastra a pessoa nesse departamento', async () => {
    await colaboradores.abrir();
    await colaboradores.cadastrar(pessoa);

    await expect(colaboradores.mensagemDeSucesso).toHaveText(`${pessoa.nome} cadastrado.`);
    await expect(colaboradores.painel).toHaveCount(0); // o painel fechou
  });

  await test.step('o departamento agora tem 1 pessoa, e excluir manda para as pessoas dele', async () => {
    await departamentos.abrir();
    await expect(departamentos.linha(DEPARTAMENTO)).toContainText('1 pessoa');

    await departamentos.pedirParaExcluir(DEPARTAMENTO);
    const aviso = rafael.getByRole('alert');
    await expect(aviso).toContainText(`${DEPARTAMENTO} ainda tem 1 pessoa.`);
    await expect(rafael.getByRole('button', { name: 'Excluir departamento' })).toHaveCount(0);

    await aviso.getByRole('link', { name: `Ver as pessoas de ${DEPARTAMENTO}` }).click();
    await expect(rafael).toHaveURL(/\/cadastros\/colaboradores\?departamentoId=\d+$/);
    await expect(colaboradores.nomesNaTabela).toHaveText([pessoa.nome]);
  });

  await test.step('a pessoa nova entra no Crona com a senha inicial', async () => {
    const novata = await (await browser.newContext()).newPage();
    await new LoginPagina(novata).entrar(contaDaPessoa);
    await novata.context().close();
  });

  await test.step('Rafael desativa o acesso dela (com confirmação), e ela vai para os Inativos', async () => {
    await colaboradores.editar(pessoa.nome);
    await colaboradores.painel.getByRole('button', { name: `Desativar acesso de ${primeiro}` }).click();
    await colaboradores.painel.getByRole('button', { name: 'Desativar acesso', exact: true }).click();

    await expect(colaboradores.mensagemDeSucesso).toHaveText(`Acesso de ${pessoa.nome} desativado.`);
    await expect(colaboradores.nomesNaTabela).toHaveCount(0); // a lista mostra os ativos
    await colaboradores.filtrarPor('Inativos');
    await expect(colaboradores.nomesNaTabela).toHaveText([pessoa.nome]);
  });

  await test.step('com o acesso desativado, a pessoa não entra mais', async () => {
    const novata = await (await browser.newContext()).newPage();
    await new LoginPagina(novata).tentarEntrar(pessoa.email, pessoa.senha);

    await expect(novata.getByRole('alert')).toBeVisible();
    await expect(novata).toHaveURL(/\/login$/);
    await novata.context().close();
  });

  await test.step('o departamento diz "Ninguém ainda", mas excluir explica que há gente inativa nele', async () => {
    await departamentos.abrir();
    await expect(departamentos.linha(DEPARTAMENTO)).toContainText('Ninguém ainda');

    await departamentos.pedirParaExcluir(DEPARTAMENTO);
    await rafael.getByRole('button', { name: 'Excluir departamento' }).click();

    await expect(rafael.getByRole('alert')).toContainText('mesmo com o acesso desativado');
    await expect(departamentos.linha(DEPARTAMENTO)).toHaveCount(1); // continua na lista
  });
});

/** BUG-06, pela tela: no painel dele mesmo, o RH não encontra como se trancar para fora. */
test('Rafael (RH) abre o próprio cadastro: não há "Desativar", e o perfil é só para leitura', async ({ page }) => {
  await new LoginPagina(page).entrar(RAFAEL);
  const colaboradores = new ColaboradoresPagina(page);
  await colaboradores.abrir();

  await colaboradores.editar(RAFAEL.nome);

  const painel = colaboradores.painel;
  await expect(painel.getByRole('heading', { name: RAFAEL.nome })).toBeVisible();
  await expect(painel.getByRole('button', { name: /^Desativar acesso/ })).toHaveCount(0);
  await expect(painel).toContainText('Este é o seu acesso. Para desativá-lo, peça para outra pessoa do RH.');
  await expect(painel.getByRole('radio')).toHaveCount(0);
  await expect(painel).toContainText('Administrador (RH)');
});
