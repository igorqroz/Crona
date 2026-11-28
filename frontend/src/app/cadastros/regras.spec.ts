import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { of, throwError } from 'rxjs';

import { DepartamentoResumo, RegrasDaEmpresa, RegrasDaEmpresaComContexto } from '../nucleo/modelos';
import { CadastrosApi } from './cadastros-api';
import { Regras } from './regras';

/*
 * Tela Cadastros > Regras de férias, do RH (teste de componente).
 *
 * As contas e as frases (limite por departamento, primeiro dia possível, validação) já são testadas
 * em regras.regras.spec.ts. Aqui o foco é a tela: as chaves, a prévia ao vivo, a barra "Alterações
 * não salvas", o que vai para a API e o aviso da EMP-06.
 *
 * "Hoje" é sexta, 27/11/2026.
 */
describe('Regras', () => {
  const salvas: RegrasDaEmpresaComContexto = {
    percentualMinimoTrabalhando: 70,
    antecedenciaMinimaDias: 30,
    limiteFeriasSimultaneasEmpresa: null,
    exigeConfirmacaoRh: false,
    pessoasAtivas: 6,
    pedidosAguardandoRh: 0,
  };
  const departamentos: DepartamentoResumo[] = [
    { id: 1, nome: 'Engenharia', pessoas: 5, limiteFora: 1 },
    { id: 2, nome: 'Comercial', pessoas: 0, limiteFora: 1 },
    { id: 5, nome: 'RH', pessoas: 1, limiteFora: 1 },
  ];

  let api: Record<'regras' | 'departamentos' | 'salvarRegras', ReturnType<typeof vi.fn>>;

  beforeEach(() => {
    vi.useFakeTimers({ toFake: ['Date'] });
    vi.setSystemTime(new Date(2026, 10, 27, 10, 0));
    api = {
      regras: vi.fn(() => of(salvas)),
      departamentos: vi.fn(() => of(departamentos)),
      salvarRegras: vi.fn((r: RegrasDaEmpresa) => of({ ...salvas, ...r })),
    };
  });

  afterEach(() => vi.useRealTimers());

  /** Espera os setTimeout(0) da tela (o foco muda depois que o Angular desenha). */
  const esperar = () => new Promise((r) => setTimeout(r, 0));

  function criar() {
    TestBed.configureTestingModule({
      imports: [Regras],
      providers: [{ provide: CadastrosApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(Regras);
    fixture.detectChanges();
    const elemento = fixture.nativeElement as HTMLElement;

    const cartao = (titulo: 'antecedencia' | 'departamento' | 'empresa' | 'rh') =>
      elemento.querySelector(`section[aria-labelledby="titulo-${titulo}"]`) as HTMLElement;
    const chave = (titulo: 'departamento' | 'empresa' | 'rh') =>
      cartao(titulo).querySelector('[role="switch"]') as HTMLButtonElement;
    const alternar = (titulo: 'departamento' | 'empresa' | 'rh') => {
      chave(titulo).click();
      fixture.detectChanges();
    };
    const campo = (rotulo: 'antecedencia' | 'percentual' | 'limite') =>
      elemento.querySelector(`input[aria-labelledby="rotulo-${rotulo}"]`) as HTMLInputElement | null;
    const digitar = (rotulo: 'antecedencia' | 'percentual' | 'limite', valor: string) => {
      const c = campo(rotulo)!;
      c.value = valor;
      c.dispatchEvent(new Event('input'));
      fixture.detectChanges();
    };
    const barra = () => elemento.querySelector('[role="region"][aria-label="Alterações não salvas"]');
    const clicar = async (texto: string) => {
      [...elemento.querySelectorAll('button')].find((b) => b.textContent?.trim() === texto)!.click();
      fixture.detectChanges();
      await esperar();
    };

    return { fixture, elemento, cartao, chave, alternar, campo, digitar, barra, clicar };
  }

  it('ao abrir: as regras salvas, cada chave no estado certo e sem barra de salvar', () => {
    const { campo, chave, barra, cartao } = criar();

    expect(campo('antecedencia')!.value).toBe('30');
    expect(campo('percentual')!.value).toBe('70');
    expect(chave('departamento').getAttribute('aria-checked')).toBe('true');
    expect(chave('departamento').textContent?.trim()).toBe('Ligada');
    expect(chave('empresa').getAttribute('aria-checked')).toBe('false');
    expect(chave('rh').getAttribute('aria-checked')).toBe('false');
    expect(campo('limite')).toBeNull(); // desligada: o campo nem aparece
    expect(cartao('empresa').textContent).toContain('Desligada: só vale o limite de cada departamento.');
    expect(barra()).toBeNull();
  });

  it('cada chave é um switch com o título do cartão como nome', () => {
    const { chave, elemento } = criar();

    expect(chave('rh').getAttribute('aria-labelledby')).toBe('titulo-rh');
    expect(elemento.querySelector('#titulo-rh')?.textContent).toBe('Confirmação do RH');
  });

  describe('as prévias, ao vivo', () => {
    it('antecedência: diz o primeiro dia possível para um pedido feito hoje, e acompanha o número', () => {
      const { cartao, digitar } = criar();

      expect(cartao('antecedencia').querySelector('.previa')?.textContent?.replace(/\s+/g, ' ').trim()).toBe(
        'Um pedido feito hoje (27/11) pode começar a partir de 27/12/2026.',
      );

      digitar('antecedencia', '45');
      expect(cartao('antecedencia').querySelector('.previa strong')?.textContent).toBe('11/01/2027');

      digitar('antecedencia', '20'); // abaixo do mínimo: a prévia some (não mostra uma data que não vale)
      expect(cartao('antecedencia').querySelector('.previa')).toBeNull();
    });

    it('limite por departamento: "Como fica hoje" com o limite de cada um e os vazios', () => {
      const { cartao, digitar } = criar();

      const itens = () => [...cartao('departamento').querySelectorAll('.previa li')].map((li) => li.textContent?.trim());
      expect(itens()).toEqual(['Engenharia · 5 pessoas → até 1 fora por dia', 'RH · 1 pessoa → até 1 fora por dia']);
      expect(cartao('departamento').textContent).toContain('Comercial ainda não tem ninguém.');

      digitar('percentual', '40');
      expect(itens()[0]).toBe('Engenharia · 5 pessoas → até 3 fora por dia');
    });

    it('limite da empresa ligado: a dica diz quantas pessoas ativas a empresa tem', () => {
      const { alternar, cartao } = criar();

      alternar('empresa');

      expect(cartao('empresa').querySelector('#dica-limite')?.textContent).toBe(
        'Hoje a empresa tem 6 pessoas com acesso ativo.',
      );
    });
  });

  describe('a barra "Alterações não salvas"', () => {
    it('aparece quando algo muda e some se voltar ao que estava salvo', () => {
      const { digitar, barra } = criar();

      digitar('antecedencia', '40');
      expect(barra()).not.toBeNull();

      digitar('antecedencia', '30');
      expect(barra()).toBeNull();
    });

    it('"Descartar" volta tudo ao que está salvo', () => {
      const { digitar, alternar, chave, campo, barra, clicar } = criar();
      digitar('antecedencia', '60');
      alternar('rh');

      void clicar('Descartar');

      expect(campo('antecedencia')!.value).toBe('30');
      expect(chave('rh').getAttribute('aria-checked')).toBe('false');
      expect(barra()).toBeNull();
    });

    it('desligar e ligar de novo: o valor digitado fica guardado', () => {
      const { digitar, alternar, campo, barra } = criar();
      digitar('percentual', '55');

      alternar('departamento');
      expect(campo('percentual')).toBeNull();
      alternar('departamento');

      expect(campo('percentual')!.value).toBe('55');
      expect(barra()).not.toBeNull(); // 55 é diferente dos 70 salvos
    });
  });

  describe('salvar', () => {
    it('manda as 4 regras de uma vez (desligada = nulo), mostra o sucesso com o foco e some a barra', async () => {
      const { digitar, alternar, clicar, elemento, barra } = criar();
      digitar('antecedencia', '45');
      alternar('departamento'); // desliga a EMP-01
      alternar('empresa');
      digitar('limite', '3');
      alternar('rh');

      await clicar('Salvar regras');

      expect(api.salvarRegras).toHaveBeenCalledWith({
        antecedenciaMinimaDias: 45,
        percentualMinimoTrabalhando: null,
        limiteFeriasSimultaneasEmpresa: 3,
        exigeConfirmacaoRh: true,
      });
      expect(elemento.querySelector('#feito')?.textContent?.trim()).toBe('Regras salvas. Valem para os próximos pedidos.');
      expect(document.activeElement?.id).toBe('feito');
      expect(barra()).toBeNull();
    });

    it('com erro: mostra o erro ligado ao campo, leva o foco até ele e não chama a API', async () => {
      const { digitar, clicar, campo, elemento } = criar();
      digitar('antecedencia', '20');

      await clicar('Salvar regras');

      expect(api.salvarRegras).not.toHaveBeenCalled();
      expect(campo('antecedencia')!.getAttribute('aria-invalid')).toBe('true');
      expect(campo('antecedencia')!.getAttribute('aria-describedby')).toBe('erro-antecedencia');
      expect(elemento.querySelector('#erro-antecedencia')?.textContent).toBe('A antecedência mínima é de 30 dias.');
      expect(document.activeElement).toBe(campo('antecedencia'));
    });

    it('corrigir o campo tira o erro dele', async () => {
      const { digitar, clicar, campo, elemento } = criar();
      digitar('antecedencia', '20');
      await clicar('Salvar regras');

      digitar('antecedencia', '35');

      expect(campo('antecedencia')!.getAttribute('aria-invalid')).toBeNull();
      expect(elemento.querySelector('#erro-antecedencia')).toBeNull();
    });

    it('campo vazio também é erro (não vira 0 nem some em silêncio)', async () => {
      const { digitar, clicar, elemento } = criar();
      digitar('antecedencia', '');

      await clicar('Salvar regras');

      expect(api.salvarRegras).not.toHaveBeenCalled();
      expect(elemento.querySelector('#erro-antecedencia')).not.toBeNull();
    });

    it('a API recusa: a frase dela aparece num alerta, e as alterações continuam na tela', async () => {
      api.salvarRegras.mockReturnValue(
        throwError(() => new HttpErrorResponse({ status: 422, error: { detail: 'Regra da empresa recusada.' } })),
      );
      const { digitar, clicar, elemento, barra } = criar();
      digitar('antecedencia', '45');

      await clicar('Salvar regras');

      expect(elemento.querySelector('.aviso-erro[role="alert"]')?.textContent?.trim()).toBe('Regra da empresa recusada.');
      expect(barra()).not.toBeNull();
    });
  });

  describe('EMP-06: desligar a confirmação do RH com pedidos na fila', () => {
    it('avisa que os pedidos que já esperam o RH continuam na fila (decisão do Igor)', () => {
      api.regras.mockReturnValue(of({ ...salvas, exigeConfirmacaoRh: true, pedidosAguardandoRh: 2 }));
      const { alternar, cartao } = criar();
      expect(cartao('rh').querySelector('[role="note"]')).toBeNull(); // ligada: nada a avisar

      alternar('rh');

      const aviso = cartao('rh').querySelector('[role="note"]')!;
      expect(aviso.textContent).toContain('2 pedidos estão esperando a confirmação do RH.');
      expect(aviso.textContent).toContain('Eles continuam na aba "Confirmação do RH" até alguém confirmar.');
    });

    it('no singular, com 1 pedido', () => {
      api.regras.mockReturnValue(of({ ...salvas, exigeConfirmacaoRh: true, pedidosAguardandoRh: 1 }));
      const { alternar, cartao } = criar();

      alternar('rh');

      expect(cartao('rh').querySelector('[role="note"]')?.textContent).toContain('1 pedido está esperando');
    });

    it('sem pedidos na fila, ou se a confirmação já estava desligada: nenhum aviso', () => {
      api.regras.mockReturnValue(of({ ...salvas, exigeConfirmacaoRh: true, pedidosAguardandoRh: 0 }));
      const { alternar, cartao } = criar();

      alternar('rh');

      expect(cartao('rh').querySelector('[role="note"]')).toBeNull();
    });

    it('a confirmação já estava desligada (os pedidos da fila são de antes): nenhum aviso', () => {
      api.regras.mockReturnValue(of({ ...salvas, exigeConfirmacaoRh: false, pedidosAguardandoRh: 2 }));
      const { cartao } = criar();

      expect(cartao('rh').querySelector('[role="note"]')).toBeNull();
    });
  });

  it('erro ao carregar: avisa, e "Tentar de novo" busca outra vez', () => {
    api.regras.mockReturnValueOnce(throwError(() => new HttpErrorResponse({ status: 500 })));
    const { fixture, elemento, campo } = criar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain('Não foi possível carregar as regras.');
    elemento.querySelector<HTMLButtonElement>('[role="alert"] button')!.click();
    fixture.detectChanges();

    expect(campo('antecedencia')!.value).toBe('30');
  });
});
