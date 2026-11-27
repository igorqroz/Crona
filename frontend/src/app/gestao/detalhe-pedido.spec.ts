import { TestBed } from '@angular/core/testing';

import { DetalheDaAprovacao } from '../nucleo/modelos';
import { DetalhePedido } from './detalhe-pedido';

/*
 * O detalhe de um pedido na tela Aprovações. Bug achado pelo E2E: clicar de novo no mesmo
 * pedido da lista recarregava o detalhe e apagava a mensagem que o gestor já tinha começado.
 */
describe('DetalhePedido', () => {
  function detalheDoPedido(id: number, nome: string): DetalheDaAprovacao {
    return {
      pedido: {
        id,
        colaborador: { id: 10 + id, nome },
        departamento: 'Engenharia',
        inicio: '2027-03-01',
        fim: '2027-03-15',
        diasGozo: 15,
        diasAbono: 0,
        periodo: 1,
        recado: null,
        enviadoEm: new Date().toISOString(),
        situacao: 'SEM_CONFLITO',
        alertas: [],
      },
      livresNoPeriodo: 30,
      voltaAoTrabalho: '2027-03-16',
      concederAte: '2027-12-31',
      limiteDoDepartamento: 3,
      verificacoes: [],
      colegasNoPeriodo: [],
    };
  }

  function criar(detalhe: DetalheDaAprovacao = detalheDoPedido(1, 'Ana Lima')) {
    TestBed.configureTestingModule({ imports: [DetalhePedido] });
    const fixture = TestBed.createComponent(DetalhePedido);
    fixture.componentRef.setInput('detalhe', detalhe);
    fixture.detectChanges();
    const caixa = () => fixture.nativeElement.querySelector('textarea') as HTMLTextAreaElement;
    const digitar = (texto: string) => {
      caixa().value = texto;
      caixa().dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    return { fixture, caixa, digitar };
  }

  it('o MESMO pedido recarregado mantém a mensagem que o gestor está escrevendo', () => {
    const { fixture, caixa, digitar } = criar();
    digitar('Pode começar uma semana depois?');

    fixture.componentRef.setInput('detalhe', detalheDoPedido(1, 'Ana Lima')); // objeto novo, mesmo id
    fixture.detectChanges();

    expect(caixa().value).toBe('Pode começar uma semana depois?');
  });

  it('outro pedido escolhido começa com a mensagem em branco', () => {
    const { fixture, caixa, digitar } = criar();
    digitar('Pode começar uma semana depois?');

    fixture.componentRef.setInput('detalhe', detalheDoPedido(2, 'Bruno Teles'));
    fixture.detectChanges();

    expect(caixa().value).toBe('');
  });

  /*
   * Teste manual do Igor (PR #32): a conta do "Fora no dia" é só do departamento do pedido (como o
   * limite da EMP-01). Sem dizer qual, parecia que faltava gente de outros departamentos.
   */
  describe('o rótulo "Fora no dia"', () => {
    const rotulo = (fixture: { nativeElement: HTMLElement }) =>
      fixture.nativeElement.querySelector('.rotulo-fora') as HTMLElement;

    it('diz de qual departamento é a conta', () => {
      const { fixture } = criar();

      expect(rotulo(fixture).textContent?.replace(/\s+/g, ' ').trim()).toBe(
        'Fora no dia (Engenharia)',
      );
      expect(rotulo(fixture).getAttribute('title')).toBe('Fora no dia (Engenharia)');
    });

    it('pedido de quem não tem departamento: só "Fora no dia"', () => {
      const semDepartamento = detalheDoPedido(1, 'Ana Lima');
      semDepartamento.pedido = { ...semDepartamento.pedido, departamento: null };
      const { fixture } = criar(semDepartamento);

      expect(rotulo(fixture).textContent?.trim()).toBe('Fora no dia');
      expect(rotulo(fixture).querySelector('small')).toBeNull();
      expect(rotulo(fixture).getAttribute('title')).toBe('Fora no dia');
    });
  });
});
