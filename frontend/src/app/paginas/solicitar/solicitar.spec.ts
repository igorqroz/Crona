import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Observable, of, Subject, throwError } from 'rxjs';

import { FeriasApi } from '../../ferias/ferias-api';
import { CalendarioDaEquipe, PeriodoAquisitivo, Simulacao } from '../../nucleo/modelos';
import { Solicitar } from './solicitar';

/*
 * Tela "Solicitar férias" (teste de componente).
 *
 * As regras do calendário já são testadas em calendario.regras.spec.ts. Aqui o foco é a TELA:
 * o que ela pede para a API, o que mostra com a resposta e o que faz quando a API recusa.
 *
 * A FeriasApi é um DUBLÊ (fake): cada método é um vi.fn() que devolve um Observable pronto.
 * Assim o teste não depende do back-end, e dá para simular erros que seriam difíceis de
 * provocar de verdade (um 422 no envio, uma resposta que chega atrasada).
 *
 * A data de "hoje" é fixada em 01/03/2027 (vi.setSystemTime): a tela mostra março e abril de
 * 2027, e os dias clicados no teste nunca viram "passado".
 */
describe('Solicitar', () => {
  const periodo: PeriodoAquisitivo = {
    numero: 1,
    inicio: '2026-01-10',
    fim: '2027-01-09',
    tirarAte: '2027-12-09',
    faltasInjustificadas: 0,
    direito: 30,
    aprovados: 0,
    emPedido: 0,
    livres: 30,
    situacao: 'DISPONIVEL',
  };

  const calendario: CalendarioDaEquipe = {
    departamento: 'Engenharia',
    limite: 2,
    gestor: { id: 2, nome: 'Roberto Neves' },
    ausencias: [],
    feriados: [],
  };

  function simulacao(extra: Partial<Simulacao> = {}): Simulacao {
    return {
      inicio: '2027-03-15',
      fim: '2027-03-29',
      voltaAoTrabalho: '2027-03-30',
      periodo: 1,
      livresNoPeriodo: 30,
      podeEnviar: true,
      resumo: 'Tudo certo para enviar.',
      verificacoes: [
        {
          regra: 'CLT-03',
          resultado: 'OK',
          mensagem: 'Começa pelo menos 2 dias antes de um feriado.',
        },
      ],
      colegasNoPeriodo: [],
      ...extra,
    };
  }

  /** Um erro HTTP como o HttpClient entrega: o corpo (ProblemDetail) fica em `error`. */
  function erroHttp(status: number, corpo: object | null = null): Observable<never> {
    return throwError(() => new HttpErrorResponse({ status, error: corpo }));
  }

  let api: {
    periodos: ReturnType<typeof vi.fn>;
    calendario: ReturnType<typeof vi.fn>;
    simular: ReturnType<typeof vi.fn>;
    enviar: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] }); // só o relógio; os timers continuam de verdade
    vi.setSystemTime(new Date(2027, 2, 1, 10, 0)); // 01/03/2027 (no Date, março = 2)
    api = {
      periodos: vi.fn(() => of([periodo])),
      calendario: vi.fn(() => of(calendario)),
      simular: vi.fn(() => of(simulacao())),
      enviar: vi.fn(() => of({})),
    };
  });

  afterEach(() => vi.useRealTimers());

  function criar() {
    TestBed.configureTestingModule({
      imports: [Solicitar],
      providers: [provideRouter([]), { provide: FeriasApi, useValue: api }],
    });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Solicitar);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;

    /** Clica num dia do calendário pelo nome que o leitor de tela fala ("15 de março, ..."). */
    const clicarNoDia = (dia: number, mes: string) => {
      const botao = [...elemento.querySelectorAll<HTMLButtonElement>('button.dia')].find((b) =>
        b.getAttribute('aria-label')?.startsWith(`${dia} de ${mes},`),
      );
      if (!botao) throw new Error(`Dia ${dia} de ${mes} não está na tela`);
      botao.click();
      fixture.detectChanges();
    };

    /** O valor de uma linha do resumo: "Duração" → "15 dias". */
    const resumo = (rotulo: string) =>
      [...elemento.querySelectorAll('.linhas > div')]
        .find((linha) => linha.querySelector('dt')?.textContent?.trim() === rotulo)
        ?.querySelector('dd')
        ?.textContent?.trim();

    const botaoEnviar = () => elemento.querySelector<HTMLButtonElement>('button.enviar')!;
    const alerta = () => elemento.querySelector('[role="alert"]')?.textContent?.trim();

    return { fixture, elemento, clicarNoDia, resumo, botaoEnviar, alerta };
  }

  it('ao abrir, busca o calendário dos 2 meses na tela e o botão começa desabilitado', () => {
    const { elemento, botaoEnviar } = criar();

    expect(api.calendario).toHaveBeenCalledWith('2027-03-01', '2027-04-30');
    expect(elemento.querySelector('.meses')?.textContent).toContain('Março 2027');
    expect(elemento.querySelector('.meses')?.textContent).toContain('Abril 2027');
    expect(elemento.textContent).toContain('Saldo: 30 dias');
    expect(botaoEnviar().disabled).toBe(true);
    expect(botaoEnviar().textContent).toContain('Enviar para Roberto Neves');
  });

  it('escolher só o primeiro dia ainda não chama a simulação', () => {
    const { clicarNoDia, resumo } = criar();

    clicarNoDia(15, 'março');

    expect(api.simular).not.toHaveBeenCalled();
    expect(resumo('Último dia')).toBe('escolha no calendário');
  });

  it('com início e fim, simula o pedido e mostra o resumo e o checklist', () => {
    const { elemento, clicarNoDia, resumo, botaoEnviar } = criar();

    clicarNoDia(15, 'março');
    clicarNoDia(29, 'março');

    expect(api.simular).toHaveBeenCalledWith({ inicio: '2027-03-15', diasGozo: 15, diasAbono: 0 });
    expect(resumo('Duração')).toBe('15 dias');
    expect(resumo('Volta ao trabalho')).toBe('30/03 · terça');
    expect(resumo('Saldo depois')).toBe('15 dias');
    expect(elemento.querySelector('crona-checklist')?.textContent).toContain(
      'Tudo certo para enviar.',
    );
    expect(botaoEnviar().disabled).toBe(false);
  });

  it('mais de 30 dias: avisa na tela e nem chama a API', () => {
    const { clicarNoDia, alerta, botaoEnviar } = criar();

    clicarNoDia(2, 'março'); // amanhã: o primeiro dia que dá para escolher
    clicarNoDia(5, 'abril'); // 02/03 a 05/04 = 35 dias

    expect(api.simular).not.toHaveBeenCalled();
    expect(alerta()).toBe('Um período de férias tem no máximo 30 dias. Você escolheu 35.');
    expect(botaoEnviar().disabled).toBe(true);
  });

  it('simulação com impedimento: mostra o motivo e não deixa enviar', () => {
    api.simular.mockReturnValue(
      of(
        simulacao({
          podeEnviar: false,
          resumo: 'Há 1 impedimento.',
          verificacoes: [
            {
              regra: 'CLT-04',
              resultado: 'IMPEDIMENTO',
              mensagem: 'Um dos períodos precisa ter 14 dias ou mais.',
            },
          ],
        }),
      ),
    );
    const { elemento, clicarNoDia, botaoEnviar } = criar();

    clicarNoDia(15, 'março');
    clicarNoDia(19, 'março');

    const impedimento = elemento.querySelector('crona-checklist li.IMPEDIMENTO');
    expect(impedimento?.textContent).toContain('Um dos períodos precisa ter 14 dias ou mais.');
    expect(botaoEnviar().disabled).toBe(true);
  });

  it('a API recusa a simulação: mostra a mensagem dela', () => {
    api.simular.mockReturnValue(erroHttp(400, { detail: 'A data de início é inválida.' }));
    const { clicarNoDia, alerta } = criar();

    clicarNoDia(15, 'março');
    clicarNoDia(29, 'março');

    expect(alerta()).toBe('A data de início é inválida.');
  });

  it('vender o abono simula de novo com 10 dias vendidos', () => {
    const { elemento, fixture, clicarNoDia, resumo } = criar();
    clicarNoDia(15, 'março');
    clicarNoDia(29, 'março');

    const abono = elemento.querySelector<HTMLInputElement>('.abono input')!;
    expect(abono.parentElement?.textContent).toContain('Vender 10 dias');
    abono.click(); // marca o checkbox e dispara o (change)
    fixture.detectChanges();

    expect(api.simular).toHaveBeenLastCalledWith({
      inicio: '2027-03-15',
      diasGozo: 15,
      diasAbono: 10,
    });
    expect(resumo('Saldo depois')).toBe('5 dias'); // 30 - 15 de férias - 10 vendidos
  });

  it('enviar manda o pedido com o recado e volta para o Início', () => {
    const { elemento, fixture, clicarNoDia, botaoEnviar } = criar();
    clicarNoDia(15, 'março');
    clicarNoDia(29, 'março');

    const recado = elemento.querySelector('textarea')!;
    recado.value = '  viagem já comprada  ';
    recado.dispatchEvent(new Event('input'));
    botaoEnviar().click();
    fixture.detectChanges();

    expect(api.enviar).toHaveBeenCalledWith({
      inicio: '2027-03-15',
      diasGozo: 15,
      diasAbono: 0,
      recado: 'viagem já comprada',
    });
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/inicio']);
  });

  it('enviar sem recado não manda o campo recado', () => {
    const { fixture, clicarNoDia, botaoEnviar } = criar();
    clicarNoDia(15, 'março');
    clicarNoDia(29, 'março');

    botaoEnviar().click();
    fixture.detectChanges();

    expect(api.enviar).toHaveBeenCalledWith({
      inicio: '2027-03-15',
      diasGozo: 15,
      diasAbono: 0,
      recado: undefined,
    });
  });

  it('o envio é recusado (422): mostra o motivo, o checklist que veio no erro, e não sai da tela', () => {
    api.enviar.mockReturnValue(
      erroHttp(422, {
        detail: 'Você já tem férias pedidas nesse período.',
        verificacoes: [
          {
            regra: 'CRONA-01',
            resultado: 'IMPEDIMENTO',
            mensagem: 'Já existe um pedido seu de 15/03 a 29/03.',
          },
        ],
      }),
    );
    const { elemento, fixture, clicarNoDia, alerta, botaoEnviar } = criar();
    clicarNoDia(15, 'março');
    clicarNoDia(29, 'março');

    botaoEnviar().click();
    fixture.detectChanges();

    expect(alerta()).toBe('Você já tem férias pedidas nesse período.');
    expect(elemento.querySelector('crona-checklist')?.textContent).toContain(
      'Já existe um pedido seu de 15/03 a 29/03.',
    );
    expect(botaoEnviar().textContent).toContain('Enviar para Roberto Neves'); // saiu do "Enviando…"
    expect(TestBed.inject(Router).navigate).not.toHaveBeenCalled();
  });

  it('uma simulação que chega atrasada é descartada: vale a do último clique', () => {
    // Cada chamada a simular() devolve um Subject: o teste decide QUANDO cada resposta chega.
    const primeira = new Subject<Simulacao>();
    const segunda = new Subject<Simulacao>();
    api.simular.mockReturnValueOnce(primeira).mockReturnValueOnce(segunda);
    const { fixture, clicarNoDia, resumo } = criar();

    clicarNoDia(15, 'março');
    clicarNoDia(29, 'março'); // 1ª simulação (15 dias), ainda sem resposta
    clicarNoDia(16, 'março');
    clicarNoDia(20, 'março'); // 2ª simulação (5 dias)

    segunda.next(
      simulacao({ inicio: '2027-03-16', fim: '2027-03-20', voltaAoTrabalho: '2027-03-22' }),
    );
    primeira.next(simulacao()); // a 1ª chega DEPOIS: não pode sobrescrever a 2ª
    fixture.detectChanges();

    expect(resumo('Duração')).toBe('5 dias');
    expect(resumo('Volta ao trabalho')).toBe('22/03 · segunda');
  });

  it('erro ao carregar o calendário: avisa na tela', () => {
    api.calendario.mockReturnValue(erroHttp(500));
    const { alerta } = criar();

    expect(alerta()).toBe('Não foi possível carregar o calendário da equipe.');
  });

  it('a seta "Próximo mês" avança um mês e busca o calendário de novo', () => {
    const { elemento, fixture } = criar();

    elemento.querySelector<HTMLButtonElement>('[aria-label="Próximo mês"]')!.click();
    fixture.detectChanges();

    expect(api.calendario).toHaveBeenLastCalledWith('2027-04-01', '2027-05-31');
    expect(elemento.querySelector('.meses')?.textContent).toContain('Maio 2027');
  });
});
