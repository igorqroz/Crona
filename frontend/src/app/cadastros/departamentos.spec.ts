import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { Observable, of, throwError } from 'rxjs';

import { DepartamentoResumo } from '../nucleo/modelos';
import { CadastrosApi } from './cadastros-api';
import { Departamentos } from './departamentos';

/*
 * Tela Cadastros > Departamentos, do RH (teste de componente).
 *
 * As frases ("5 pessoas · até 1 fora por dia", o aviso de exclusão) já são testadas em
 * departamentos.regras.spec.ts. Aqui o foco é a tela: o que abre e fecha, o que vai para a API,
 * o que aparece depois e para onde vai o FOCO (quem usa teclado ou leitor de tela não pode se perder).
 */
describe('Departamentos', () => {
  const comercial: DepartamentoResumo = { id: 3, nome: 'Comercial', pessoas: 0, limiteFora: 1 };
  const engenharia: DepartamentoResumo = { id: 1, nome: 'Engenharia', pessoas: 5, limiteFora: 1 };
  const rh: DepartamentoResumo = { id: 2, nome: 'RH', pessoas: 1, limiteFora: 1 };

  let api: Record<
    'departamentos' | 'criarDepartamento' | 'renomearDepartamento' | 'excluirDepartamento',
    ReturnType<typeof vi.fn>
  >;

  beforeEach(() => {
    api = {
      departamentos: vi.fn(() => of([comercial, engenharia, rh])),
      criarDepartamento: vi.fn(() => of({ id: 9, nome: 'Jurídico' })),
      renomearDepartamento: vi.fn(() => of({ id: 3, nome: 'Vendas' })),
      excluirDepartamento: vi.fn(() => of(undefined)),
    };
  });

  function erroHttp(status: number, corpo: object): Observable<never> {
    return throwError(() => new HttpErrorResponse({ status, error: corpo }));
  }

  async function criar() {
    TestBed.configureTestingModule({
      imports: [Departamentos],
      // provideRouter: o aviso de excluir tem o link "Ver as pessoas" (para Colaboradores).
      providers: [provideRouter([]), { provide: CadastrosApi, useValue: api }],
    });
    const fixture = TestBed.createComponent(Departamentos);
    fixture.detectChanges();
    await fixture.whenStable();
    const elemento = fixture.nativeElement as HTMLElement;

    /** Atualiza a tela e espera o foco (que só muda depois que o Angular desenha). */
    const estabilizar = async () => {
      fixture.detectChanges();
      await fixture.whenStable();
    };
    /** O botão pelo texto ou pelo nome acessível (aria-label). */
    const botao = (nome: string) =>
      [...elemento.querySelectorAll('button')].find(
        (b) => b.getAttribute('aria-label') === nome || b.textContent?.trim() === nome,
      ) as HTMLButtonElement | undefined;
    const clicar = async (nome: string) => {
      botao(nome)!.click();
      await estabilizar();
    };
    const campo = () => elemento.querySelector('input') as HTMLInputElement;
    const digitar = async (texto: string) => {
      campo().value = texto;
      campo().dispatchEvent(new Event('input'));
      await estabilizar();
    };
    const enviar = async () => {
      elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
      await estabilizar();
    };
    const focado = () => document.activeElement as HTMLElement;
    const sucesso = () => elemento.querySelector('[role="status"]')?.textContent?.trim();
    /** Cada linha como "nome | resumo" (são dois elementos lado a lado). */
    const linhas = () =>
      [...elemento.querySelectorAll('li.departamento .nome')].map((n) =>
        [...n.children].map((parte) => parte.textContent?.trim()).join(' | '),
      );

    return { fixture, elemento, botao, clicar, campo, digitar, enviar, focado, sucesso, linhas, estabilizar };
  }

  it('ao abrir: a lista com o resumo de cada departamento e o total', async () => {
    const { elemento, linhas } = await criar();

    expect(elemento.querySelector('h1')?.textContent).toBe('Departamentos');
    expect(elemento.querySelector('.topo-pagina p')?.textContent).toBe(
      'Cada pessoa fica num departamento. É por ele que o Crona conta quantas pessoas podem sair de férias no mesmo dia.',
    );
    expect(elemento.querySelector('#titulo-lista')?.textContent).toBe('3 departamentos');
    expect(linhas()).toEqual([
      'Comercial | Ninguém ainda',
      'Engenharia | 5 pessoas · até 1 fora por dia',
      'RH | 1 pessoa · até 1 fora por dia',
    ]);
  });

  it('cada botão da linha diz de qual departamento é (para o leitor de tela)', async () => {
    const { botao } = await criar();

    expect(botao('Renomear Engenharia')).toBeDefined();
    expect(botao('Excluir Engenharia')).toBeDefined();
    expect(botao('Excluir RH')).toBeDefined();
  });

  describe('criar', () => {
    it('"Novo departamento" abre o campo, com nome, e o foco vai para ele', async () => {
      const { clicar, campo, focado, botao } = await criar();

      await clicar('Novo departamento');

      expect(campo().closest('label')?.textContent).toContain('Nome do novo departamento');
      // Nome acessível só com o rótulo (o erro, quando houver, vem na descrição, sem leitura dupla).
      expect(document.getElementById(campo().getAttribute('aria-labelledby')!)?.textContent).toBe(
        'Nome do novo departamento',
      );
      expect(focado()).toBe(campo());
      expect(botao('Novo departamento')!.disabled).toBe(true); // não abre dois
    });

    it('sem nome: mostra o erro ligado ao campo e não chama a API; digitar apaga o erro', async () => {
      const { clicar, enviar, digitar, campo, elemento } = await criar();
      await clicar('Novo departamento');

      await enviar();
      expect(elemento.querySelector('#erro-novo')?.textContent).toBe('Informe o nome do departamento.');
      expect(campo().getAttribute('aria-invalid')).toBe('true');
      expect(campo().getAttribute('aria-describedby')).toBe('erro-novo');
      expect(api.criarDepartamento).not.toHaveBeenCalled();

      await digitar('J');
      expect(elemento.querySelector('#erro-novo')).toBeNull();
      expect(campo().getAttribute('aria-invalid')).toBeNull();
    });

    it('com nome: manda sem os espaços, fecha, mostra o sucesso (com o foco) e recarrega a lista', async () => {
      const { clicar, digitar, enviar, sucesso, focado, elemento } = await criar();
      await clicar('Novo departamento');

      await digitar('  Jurídico  ');
      await enviar();

      expect(api.criarDepartamento).toHaveBeenCalledWith('Jurídico');
      expect(elemento.querySelector('#campo-novo')).toBeNull();
      expect(sucesso()).toBe('Departamento Jurídico criado.');
      expect(focado().id).toBe('feito');
      expect(api.departamentos).toHaveBeenCalledTimes(2);
    });

    it('a API recusa (409, nome repetido): a frase da API aparece e o formulário fica aberto', async () => {
      api.criarDepartamento.mockReturnValue(
        erroHttp(409, { detail: 'Já existe um departamento chamado "engenharia".' }),
      );
      const { clicar, digitar, enviar, elemento, campo } = await criar();
      await clicar('Novo departamento');

      await digitar('engenharia');
      await enviar();

      expect(elemento.querySelector('#erro-novo')?.textContent).toBe(
        'Já existe um departamento chamado "engenharia".',
      );
      expect(campo().value).toBe('engenharia');
      expect(api.departamentos).toHaveBeenCalledTimes(1);
    });

    it('Esc fecha o campo e o foco volta para o botão "Novo departamento"', async () => {
      const { clicar, campo, estabilizar, focado, elemento } = await criar();
      await clicar('Novo departamento');

      campo().dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
      await estabilizar();

      expect(elemento.querySelector('#campo-novo')).toBeNull();
      expect(focado().id).toBe('botao-novo');
    });
  });

  describe('renomear', () => {
    it('abre na própria linha com o nome atual e o foco no campo', async () => {
      const { clicar, campo, focado } = await criar();

      await clicar('Renomear Comercial');

      expect(campo().id).toBe('campo-renomear-3');
      expect(campo().value).toBe('Comercial');
      expect(campo().closest('label')?.textContent).toContain('Novo nome para Comercial');
      // Nome acessível só com o rótulo; a dica ("As pessoas e os pedidos continuam...") é a descrição.
      expect(document.getElementById(campo().getAttribute('aria-labelledby')!)?.textContent).toBe(
        'Novo nome para Comercial',
      );
      expect(document.getElementById(campo().getAttribute('aria-describedby')!)?.textContent).toContain(
        'só o nome muda',
      );
      expect(focado()).toBe(campo());
    });

    it('o mesmo nome: só fecha, sem chamar a API', async () => {
      const { clicar, enviar, elemento } = await criar();
      await clicar('Renomear Comercial');

      await enviar();

      expect(api.renomearDepartamento).not.toHaveBeenCalled();
      expect(elemento.querySelector('#campo-renomear-3')).toBeNull();
    });

    it('nome novo: manda para a API e mostra o sucesso', async () => {
      const { clicar, digitar, enviar, sucesso } = await criar();
      await clicar('Renomear Comercial');

      await digitar('Vendas');
      await enviar();

      expect(api.renomearDepartamento).toHaveBeenCalledWith(3, 'Vendas');
      expect(sucesso()).toBe('Departamento renomeado para Vendas.');
    });

    it('Cancelar fecha e o foco volta para o "Renomear" daquela linha', async () => {
      const { clicar, focado } = await criar();
      await clicar('Renomear Comercial');

      await clicar('Cancelar');

      expect(focado().id).toBe('renomear-3');
    });
  });

  describe('excluir', () => {
    it('com gente no departamento: no lugar de confirmar, explica o que fazer (e o foco vai para o aviso)', async () => {
      const { clicar, elemento, botao, focado } = await criar();

      await clicar('Excluir Engenharia');

      const aviso = elemento.querySelector('#aviso-excluir-1')!;
      expect(aviso.getAttribute('role')).toBe('alert');
      expect(aviso.textContent).toContain('Engenharia ainda tem 5 pessoas.');
      expect(botao('Excluir departamento')).toBeUndefined();
      expect(focado()).toBe(aviso);
    });

    it('o aviso leva para as pessoas do departamento (Colaboradores já filtrado)', async () => {
      const { clicar, elemento } = await criar();

      await clicar('Excluir Engenharia');

      const link = elemento.querySelector<HTMLAnchorElement>('#aviso-excluir-1 a')!;
      expect(link.textContent?.trim()).toBe('Ver as pessoas');
      expect(link.getAttribute('aria-label')).toBe('Ver as pessoas de Engenharia');
      expect(link.getAttribute('href')).toBe('/cadastros/colaboradores?departamentoId=1');
    });

    it('"Entendi" fecha o aviso e o foco volta para o "Excluir" daquela linha', async () => {
      const { clicar, elemento, focado } = await criar();
      await clicar('Excluir Engenharia');

      await clicar('Entendi');

      expect(elemento.querySelector('#aviso-excluir-1')).toBeNull();
      expect(focado().id).toBe('excluir-1');
    });

    it('sem ninguém: pede confirmação, com o foco no "Voltar" (a opção segura)', async () => {
      const { clicar, elemento, focado, botao } = await criar();

      await clicar('Excluir Comercial');

      expect(elemento.querySelector('.confirmar')?.textContent).toContain(
        'Excluir Comercial? Não dá para desfazer.',
      );
      expect(focado().id).toBe('voltar-excluir-3');
      expect(botao('Excluir departamento')).toBeDefined();
      expect(api.excluirDepartamento).not.toHaveBeenCalled();
    });

    it('confirmar exclui, mostra o sucesso e recarrega a lista', async () => {
      const { clicar, sucesso } = await criar();
      await clicar('Excluir Comercial');

      await clicar('Excluir departamento');

      expect(api.excluirDepartamento).toHaveBeenCalledWith(3);
      expect(sucesso()).toBe('Departamento Comercial excluído.');
      expect(api.departamentos).toHaveBeenCalledTimes(2);
    });

    // O caso real: a lista diz "Ninguém ainda" (só conta as ativas), mas há gente com o acesso desativado.
    it('a API recusa (409, gente inativa no departamento): a explicação aparece na confirmação', async () => {
      const explicacao =
        'O departamento "Comercial" está em uso e não pode ser excluído. Ainda há pessoas nele, mesmo com o' +
        ' acesso desativado, ou períodos restritos dele. Mude essas pessoas de departamento antes.';
      api.excluirDepartamento.mockReturnValue(erroHttp(409, { detail: explicacao }));
      const { clicar, elemento } = await criar();
      await clicar('Excluir Comercial');

      await clicar('Excluir departamento');

      expect(elemento.querySelector('.confirmar [role="alert"]')?.textContent).toBe(explicacao);
    });
  });

  it('só um formulário aberto por vez: abrir outro fecha o anterior', async () => {
    const { clicar, elemento } = await criar();
    await clicar('Renomear Comercial');

    await clicar('Excluir RH');

    expect(elemento.querySelector('#campo-renomear-3')).toBeNull();
    expect(elemento.querySelector('#aviso-excluir-2')).not.toBeNull();
    expect(elemento.querySelectorAll('li.departamento.aberto')).toHaveLength(1);
  });

  it('abrir um formulário tira a faixa de sucesso da ação anterior', async () => {
    const { clicar, sucesso } = await criar();
    await clicar('Excluir Comercial');
    await clicar('Excluir departamento');
    expect(sucesso()).toBeDefined();

    await clicar('Renomear RH');

    expect(sucesso()).toBeUndefined();
  });

  it('nenhum departamento: diz isso e oferece criar o primeiro', async () => {
    api.departamentos.mockReturnValue(of([]));
    const { elemento, clicar, campo, botao } = await criar();

    expect(elemento.querySelector('.vazio')?.textContent).toContain('Nenhum departamento ainda');
    expect(botao('Novo departamento')).toBeUndefined(); // no topo, só aparece com lista

    await clicar('Criar o primeiro departamento');

    expect(campo().id).toBe('campo-novo');
  });

  it('erro ao carregar: avisa, e "Tentar de novo" busca outra vez', async () => {
    api.departamentos.mockReturnValueOnce(erroHttp(500, {}));
    const { elemento, clicar, linhas } = await criar();

    expect(elemento.querySelector('[role="alert"]')?.textContent).toContain(
      'Não foi possível carregar os departamentos.',
    );

    await clicar('Tentar de novo');

    expect(linhas()).toHaveLength(3);
  });
});
