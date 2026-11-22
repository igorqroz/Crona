import { Negociacao, PeriodoAquisitivo, Solicitacao } from '../../nucleo/modelos';
import {
  avisosDisponiveis,
  blocos,
  passosDoPedido,
  pedidoEmFoco,
  periodoEmFoco,
  propostaParaMim,
  saldo,
} from './inicio.regras';

/*
 * Testes das regras da tela Início, com o Vitest. A estrutura é a mesma do JUnit:
 * describe = a classe de teste, it = um @Test, expect(...).toBe(...) = assertThat(...).isEqualTo(...).
 */

function periodo(numero: number, extra: Partial<PeriodoAquisitivo>): PeriodoAquisitivo {
  return {
    numero,
    inicio: '2025-01-01',
    fim: '2025-12-31',
    tirarAte: '2026-12-31',
    faltasInjustificadas: 0,
    direito: 30,
    aprovados: 0,
    emPedido: 0,
    livres: 30,
    situacao: 'DISPONIVEL',
    ...extra,
  };
}

function pedido(id: number, extra: Partial<Solicitacao>): Solicitacao {
  return {
    id,
    status: 'AGUARDANDO_GESTOR',
    inicio: '2026-03-02',
    fim: '2026-03-16',
    diasGozo: 15,
    diasAbono: 0,
    periodo: 1,
    recado: null,
    enviadoEm: '2026-02-10T11:12:00Z',
    decididoPor: null,
    mensagemDecisao: null,
    ...extra,
  };
}

describe('periodoEmFoco', () => {
  it('mostra o período mais antigo que ainda tem dias para usar', () => {
    const periodos = [
      periodo(1, { aprovados: 30, livres: 0, situacao: 'ESGOTADO' }),
      periodo(2, { livres: 15, emPedido: 15 }),
      periodo(3, { situacao: 'EM_AQUISICAO' }),
    ];

    expect(periodoEmFoco(periodos)?.numero).toBe(2);
  });

  it('sem dias para usar, mostra o período em aquisição', () => {
    const periodos = [periodo(1, { aprovados: 30, livres: 0, situacao: 'ESGOTADO' }), periodo(2, { situacao: 'EM_AQUISICAO' })];

    expect(periodoEmFoco(periodos)?.numero).toBe(2);
  });

  it('sem períodos: nulo', () => {
    expect(periodoEmFoco([])).toBeNull();
  });
});

describe('saldo e blocos', () => {
  it('o saldo é o que ainda não foi aprovado, e a barra tem um bloco por dia, na ordem', () => {
    const p = periodo(1, { aprovados: 10, emPedido: 5, livres: 15 });

    expect(saldo(p)).toBe(20);
    const barra = blocos(p);
    expect(barra).toHaveLength(30);
    expect(barra[9]).toBe('aprovado');
    expect(barra[10]).toBe('pedido');
    expect(barra[15]).toBe('livre');
  });
});

describe('pedidoEmFoco', () => {
  const hoje = '2026-02-10';

  it('prefere o pedido em análise que começa antes', () => {
    const pedidos = [
      pedido(1, { status: 'APROVADA', inicio: '2026-02-20', fim: '2026-03-01' }),
      pedido(2, { inicio: '2026-06-01', fim: '2026-06-15' }),
      pedido(3, { status: 'AGUARDANDO_RH', inicio: '2026-04-06', fim: '2026-04-20' }),
    ];

    expect(pedidoEmFoco(pedidos, hoje)?.id).toBe(3);
  });

  it('sem pedido em análise, mostra as próximas férias aprovadas; as que já passaram não contam', () => {
    const pedidos = [
      pedido(1, { status: 'APROVADA', inicio: '2026-01-05', fim: '2026-01-19' }),
      pedido(2, { status: 'APROVADA', inicio: '2026-07-01', fim: '2026-07-15' }),
      pedido(3, { status: 'CANCELADA' }),
    ];

    expect(pedidoEmFoco(pedidos, hoje)?.id).toBe(2);
  });

  it('sem nada em andamento: nulo', () => {
    expect(pedidoEmFoco([pedido(1, { status: 'RECUSADA' })], hoje)).toBeNull();
  });
});

describe('passosDoPedido', () => {
  it('com o gestor: enviado feito, gestor atual, aprovação pendente', () => {
    const passos = passosDoPedido(pedido(1, {}));

    expect(passos.map((p) => p.estado)).toEqual(['feito', 'atual', 'pendente']);
  });

  it('com o RH (EMP-06): aparece a etapa do RH como atual', () => {
    const passos = passosDoPedido(pedido(1, { status: 'AGUARDANDO_RH' }));

    expect(passos.map((p) => p.titulo)).toEqual(['Enviado', 'Aprovado pelo gestor', 'Com o RH', 'Férias aprovadas']);
    expect(passos[2].estado).toBe('atual');
  });

  it('aprovado: tudo feito', () => {
    const passos = passosDoPedido(pedido(1, { status: 'APROVADA' }));

    expect(passos.every((p) => p.estado === 'feito')).toBe(true);
  });
});

describe('avisosDisponiveis', () => {
  it('só as férias aprovadas, das mais recentes para as mais antigas', () => {
    const avisos = avisosDisponiveis([
      pedido(1, { status: 'APROVADA', inicio: '2026-07-06', fim: '2026-07-20' }),
      pedido(2, { status: 'AGUARDANDO_GESTOR' }),
      pedido(3, { status: 'APROVADA', inicio: '2027-01-11', fim: '2027-01-25' }),
    ]);

    expect(avisos.map((a) => a.solicitacaoId)).toEqual([3, 1]);
    expect(avisos[0]).toEqual({
      solicitacaoId: 3,
      titulo: 'Janeiro 2027',
      detalhe: '11/01 – 25/01 · 15 dias',
      arquivo: 'aviso-de-ferias-2027-01-11.pdf',
    });
  });
});

describe('propostaParaMim', () => {
  const negociacao: Negociacao = {
    id: 7,
    status: 'AGUARDANDO_COLEGA',
    solicitante: { id: 3, nome: 'Ana Lima' },
    colega: { id: 4, nome: 'Bruno Teles' },
    vezDe: { id: 4, nome: 'Bruno Teles' },
    pedidoDoColega: { id: 20, inicio: '2026-03-02', fim: '2026-03-16', diasGozo: 15 },
    pedidoDoSolicitante: { id: 21, inicio: '2026-04-06', fim: '2026-04-20', diasGozo: 15 },
    propostas: [
      {
        autor: { id: 3, nome: 'Ana Lima' },
        inicioSolicitante: '2026-03-02',
        fimSolicitante: '2026-03-16',
        inicioColega: '2026-04-06',
        fimColega: '2026-04-20',
        mensagem: 'Casamento da minha irmã',
        feitaEm: '2026-02-10T09:00:00Z',
      },
    ],
    decididoPor: null,
    mensagemDecisao: null,
    criadaEm: '2026-02-10T09:00:00Z',
  };

  it('para o colega (é a vez dele): as datas dele hoje e as que ele ganharia', () => {
    const proposta = propostaParaMim([negociacao], 4);

    expect(proposta?.autor.nome).toBe('Ana Lima');
    expect(proposta?.minhasHoje).toBe('02/03 – 16/03');
    expect(proposta?.minhasNovas).toBe('06/04 – 20/04');
  });

  it('para quem propôs, enquanto espera o colega: nada (não é a vez dela)', () => {
    expect(propostaParaMim([negociacao], 3)).toBeNull();
  });
});
