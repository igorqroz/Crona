import { TestBed } from '@angular/core/testing';

import { Decisao, SituacaoDoPedido, TrocaParaAprovar, Verificacao } from '../nucleo/modelos';
import { TrocaParaAprovarCartao } from './troca-para-aprovar';

/*
 * O cartão de uma troca que os dois colegas já combinaram e espera o gestor (aba Trocas
 * da tela Aprovações). O cartão mostra os dois lados e devolve a decisão para a página.
 *
 * O Bruno propôs à Ana: ele fica com 05/04–19/04 (as dela) e ela com 07/06–21/06 (as dele).
 */
describe('TrocaParaAprovarCartao', () => {
  const alerta: Verificacao = {
    regra: 'EMP-01',
    resultado: 'ATENCAO',
    mensagem: 'A Engenharia passaria do limite.',
  };
  const ok: Verificacao = { regra: 'CLT-03', resultado: 'OK', mensagem: 'Dentro do prazo.' };

  function troca(
    situacao: SituacaoDoPedido = 'SEM_CONFLITO',
    extra: Partial<TrocaParaAprovar> = {},
  ): TrocaParaAprovar {
    return {
      negociacao: {
        id: 40,
        status: 'AGUARDANDO_GESTOR',
        solicitante: { id: 3, nome: 'Bruno Teles' },
        colega: { id: 1, nome: 'Ana Lima' },
        vezDe: null,
        pedidoDoColega: { id: 100, inicio: '2027-04-05', fim: '2027-04-19', diasGozo: 15 },
        pedidoDoSolicitante: { id: 200, inicio: '2027-06-07', fim: '2027-06-21', diasGozo: 15 },
        propostas: [
          {
            autor: { id: 3, nome: 'Bruno Teles' },
            inicioSolicitante: '2027-04-05',
            fimSolicitante: '2027-04-19',
            inicioColega: '2027-06-07',
            fimColega: '2027-06-21',
            mensagem: 'Meu filho nasce em abril.',
            feitaEm: '2027-02-28T13:00:00Z',
          },
        ],
        decididoPor: null,
        mensagemDecisao: null,
        criadaEm: '2027-02-28T13:00:00Z',
      },
      situacao,
      verificacoesSolicitante: [ok],
      verificacoesColega: situacao === 'SEM_CONFLITO' ? [ok] : [ok, alerta],
      ...extra,
    };
  }

  function criar(dados: TrocaParaAprovar = troca()) {
    TestBed.configureTestingModule({ imports: [TrocaParaAprovarCartao] });
    const fixture = TestBed.createComponent(TrocaParaAprovarCartao);
    fixture.componentRef.setInput('troca', dados);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;

    const aprovou = vi.fn<(d: Decisao) => void>();
    const recusou = vi.fn<(m: string) => void>();
    fixture.componentInstance.aprovou.subscribe(aprovou);
    fixture.componentInstance.recusou.subscribe(recusou);

    const botao = (texto: string) =>
      [...elemento.querySelectorAll('button')].find(
        (b) => b.textContent?.trim() === texto,
      ) as HTMLButtonElement;
    const clicar = (texto: string) => {
      botao(texto).click();
      fixture.detectChanges();
    };
    const escrever = (texto: string) => {
      const caixa = elemento.querySelector('textarea')!;
      caixa.value = texto;
      caixa.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const aviso = () => elemento.querySelector('[role="alert"]')?.textContent?.trim();
    return { fixture, elemento, aprovou, recusou, botao, clicar, escrever, aviso };
  }

  it('mostra os dois lados: as férias de hoje e as novas, e o recado de quem propôs', () => {
    const { elemento } = criar();

    expect(elemento.querySelector('h3')?.textContent).toBe('Bruno Teles ⇄ Ana Lima');
    // Cada lado: [nome, hoje, seta, nova], cada um no seu <span>.
    const lados = [...elemento.querySelectorAll('.lado .datas')].map((d) =>
      [...d.children].map((parte) => parte.textContent?.trim()).join(' '),
    );
    expect(lados).toEqual([
      'Bruno Teles 07/06 – 21/06 → 05/04 – 19/04',
      'Ana Lima 05/04 – 19/04 → 07/06 – 21/06',
    ]);
    expect(elemento.querySelector('.recado')?.textContent).toContain(
      '“Meu filho nasce em abril.” — Bruno Teles',
    );
  });

  it('quem propôs sem ter férias marcadas: "sem férias marcadas"', () => {
    const dados = troca();
    dados.negociacao.pedidoDoSolicitante = null;
    const { elemento } = criar(dados);

    expect(elemento.querySelector('.lado .hoje')?.textContent).toBe('sem férias marcadas');
  });

  describe('sem conflito', () => {
    it('"Aprovar troca" aprova sem precisar de mensagem', () => {
      const { clicar, aprovou } = criar();

      clicar('Aprovar troca');

      expect(aprovou).toHaveBeenCalledWith({ mesmoAssim: false, mensagem: undefined });
    });

    it('recusar sem motivo não pode: os dois colegas precisam saber por quê', () => {
      const { clicar, recusou, aviso } = criar();

      clicar('Recusar troca');

      expect(aviso()).toBe('Para recusar, escreva o motivo: os dois colegas vão ver a mensagem.');
      expect(recusou).not.toHaveBeenCalled();
    });

    it('recusar com motivo manda o motivo sem os espaços das pontas', () => {
      const { clicar, recusou, escrever } = criar();

      escrever('  Equipe no limite em abril.  ');
      clicar('Recusar troca');

      expect(recusou).toHaveBeenCalledWith('Equipe no limite em abril.');
    });
  });

  describe('com ponto de atenção', () => {
    it('o alerta aparece no lado de quem ele é (só os que não passaram)', () => {
      const { elemento } = criar(troca('COM_ATENCAO'));

      const [ladoBruno, ladoAna] = [...elemento.querySelectorAll('.lado')];
      expect(ladoBruno.querySelector('crona-checklist')).toBeNull();
      expect(ladoAna.querySelector('crona-checklist')?.textContent).toContain(
        'A Engenharia passaria do limite.',
      );
      expect(ladoAna.querySelector('crona-checklist')?.textContent).not.toContain(
        'Dentro do prazo.',
      );
      expect(elemento.querySelector('.selo')?.textContent?.trim()).toBe('Acima do limite');
    });

    it('"Aprovar mesmo assim" exige o motivo e, com ele, manda mesmoAssim', () => {
      const { clicar, aprovou, escrever, aviso } = criar(troca('COM_ATENCAO'));

      clicar('Aprovar mesmo assim');
      expect(aviso()).toBe('Para aprovar mesmo assim, escreva o motivo: ele fica registrado.');
      expect(aprovou).not.toHaveBeenCalled();

      escrever('A equipe cobre: o Caio volta antes.');
      clicar('Aprovar mesmo assim');
      expect(aprovou).toHaveBeenCalledWith({
        mesmoAssim: true,
        mensagem: 'A equipe cobre: o Caio volta antes.',
      });
    });
  });

  it('com impedimento: não há botão de aprovar, só a explicação e o Recusar', () => {
    const { elemento, botao } = criar(troca('COM_IMPEDIMENTO'));

    expect(botao('Aprovar troca')).toBeUndefined();
    expect(botao('Aprovar mesmo assim')).toBeUndefined();
    expect(elemento.querySelector('.bloqueado')?.textContent).toContain(
      'Não dá para aprovar: há impedimento.',
    );
    expect(botao('Recusar troca')).toBeDefined();
  });

  it('enquanto a decisão vai para a API, os botões ficam desabilitados', () => {
    const { fixture, botao } = criar();

    fixture.componentRef.setInput('ocupado', true);
    fixture.detectChanges();

    expect(botao('Aprovar troca').disabled).toBe(true);
    expect(botao('Recusar troca').disabled).toBe(true);
  });

  it('o erro que a página recebeu da API aparece no cartão, com o checklist', () => {
    const { fixture, elemento } = criar();

    fixture.componentRef.setInput('erro', {
      mensagem: 'A troca tem impedimento.',
      verificacoes: [{ regra: 'EMP-01', resultado: 'IMPEDIMENTO', mensagem: 'Passa do limite.' }],
    });
    fixture.detectChanges();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain(
      'A troca tem impedimento.',
    );
    // Sem conflito, os lados não têm checklist: o único na tela é o do erro.
    expect(elemento.querySelector('crona-checklist')?.textContent).toContain('Passa do limite.');
  });

  it('outra troca escolhida: a mensagem e o aviso começam em branco', () => {
    const { fixture, elemento, clicar, escrever, aviso } = criar();
    clicar('Recusar troca'); // sem motivo: aparece o aviso
    escrever('Rascunho');
    expect(aviso()).toBeDefined();

    const outra = troca();
    outra.negociacao = { ...outra.negociacao, id: 41 };
    fixture.componentRef.setInput('troca', outra);
    fixture.detectChanges();

    expect(elemento.querySelector('textarea')!.value).toBe('');
    expect(aviso()).toBeUndefined();
  });
});
