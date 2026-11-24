import { APIRequestContext, expect } from '@playwright/test';

import { Conta } from './contas';

/**
 * Ajudantes que falam DIRETO com a API (sem navegador). Servem para PREPARAR o teste:
 * descobrir dados, não para testar a tela. O que importa (clicar, ver o resultado)
 * continua sendo feito pelo navegador.
 *
 * O `request` do Playwright usa o mesmo baseURL da configuração, então "/api/..." passa
 * pelo nginx (ou pelo proxy do `npm start`), igual ao que o site faz.
 */

/** Faz login pela API e devolve o token JWT. */
export async function tokenDe(request: APIRequestContext, conta: Conta): Promise<string> {
  const resposta = await request.post('/api/auth/login', { data: { email: conta.email, senha: conta.senha } });
  expect(resposta.ok(), `login de ${conta.email} pela API`).toBeTruthy();
  return (await resposta.json()).token;
}

export interface Datas {
  /** "2027-01-11" */
  inicio: string;
  /** "2027-01-25" */
  fim: string;
}

/**
 * Acha as próximas datas que a API aceitaria para `diasGozo` dias de férias.
 *
 * Por que não deixar as datas fixas no teste? Porque o teste ia "envelhecer": uma data fixa
 * vira passado, cai num feriado ou bate com um pedido de uma execução anterior. Aqui, quem
 * diz se as datas valem é a própria API (a mesma simulação que a tela usa): tentamos as
 * próximas segundas-feiras, a partir de 5 semanas para frente, até uma passar em todas as regras.
 */
export async function proximasFeriasValidas(
  request: APIRequestContext,
  conta: Conta,
  diasGozo: number,
  /**
   * De quantos dias para frente começar a procurar. Testes que rodam em paralelo com a mesma
   * pessoa usam pontos de partida diferentes, para não escolherem as mesmas datas (CRONA-01).
   */
  aPartirDe = 35,
): Promise<Datas> {
  const token = await tokenDe(request, conta);
  let inicio = proximaSegunda(adicionarDias(hoje(), aPartirDe));

  for (let tentativa = 0; tentativa < 30; tentativa++) {
    const resposta = await request.post('/api/minhas-ferias/simulacao', {
      headers: { Authorization: `Bearer ${token}` },
      data: { inicio: iso(inicio), diasGozo, diasAbono: 0 },
    });
    if (resposta.ok() && (await resposta.json()).podeEnviar) {
      return { inicio: iso(inicio), fim: iso(adicionarDias(inicio, diasGozo - 1)) };
    }
    inicio = adicionarDias(inicio, 7);
  }
  throw new Error(`A API não aceitou nenhuma data para ${conta.nome} nas próximas 30 semanas.`);
}

/**
 * Prepara férias APROVADAS pela API: a pessoa pede e o gestor aprova, sem passar pelas telas.
 * Serve para testes que começam DEPOIS da aprovação (ex.: o RH cancelando).
 */
export async function feriasAprovadasPelaApi(
  request: APIRequestContext,
  colaborador: Conta,
  gestor: Conta,
  diasGozo: number,
  aPartirDe: number,
): Promise<Datas & { id: number }> {
  const datas = await proximasFeriasValidas(request, colaborador, diasGozo, aPartirDe);

  const pedido = await request.post('/api/minhas-ferias/solicitacoes', {
    headers: { Authorization: `Bearer ${await tokenDe(request, colaborador)}` },
    data: { inicio: datas.inicio, diasGozo, diasAbono: 0 },
  });
  expect(pedido.status(), `pedido de ${colaborador.nome} pela API`).toBe(201);
  const { id } = await pedido.json();

  const aprovacao = await request.post(`/api/aprovacoes/${id}/aprovar`, {
    headers: { Authorization: `Bearer ${await tokenDe(request, gestor)}` },
    // A mensagem cobre o caso "aprovar mesmo assim" (pedido com ponto de atenção).
    data: { mesmoAssim: true, mensagem: 'Aprovado pelo teste E2E (preparação).' },
  });
  expect(aprovacao.ok(), `aprovação de ${gestor.nome} pela API`).toBeTruthy();
  return { id, ...datas };
}

// ------------------------------------------------------------------ datas

function hoje(): Date {
  const agora = new Date();
  return new Date(agora.getFullYear(), agora.getMonth(), agora.getDate());
}

function adicionarDias(data: Date, dias: number): Date {
  return new Date(data.getFullYear(), data.getMonth(), data.getDate() + dias);
}

function proximaSegunda(data: Date): Date {
  const faltam = (8 - data.getDay()) % 7; // getDay(): 0 = domingo, 1 = segunda...
  return adicionarDias(data, faltam);
}

/** Date → "2027-01-11" (com a data local, sem passar por UTC). */
export function iso(data: Date): string {
  const mes = String(data.getMonth() + 1).padStart(2, '0');
  const dia = String(data.getDate()).padStart(2, '0');
  return `${data.getFullYear()}-${mes}-${dia}`;
}
