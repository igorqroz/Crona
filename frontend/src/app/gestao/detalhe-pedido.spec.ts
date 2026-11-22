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

  function criar() {
    TestBed.configureTestingModule({ imports: [DetalhePedido] });
    const fixture = TestBed.createComponent(DetalhePedido);
    fixture.componentRef.setInput('detalhe', detalheDoPedido(1, 'Ana Lima'));
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
});
