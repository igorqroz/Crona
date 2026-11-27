import { HttpErrorResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { Observable, of, throwError } from 'rxjs';

import { Colaborador, DadosDoColaborador, DepartamentoResumo } from '../nucleo/modelos';
import { Sessao } from '../nucleo/sessao';
import { CadastrosApi } from './cadastros-api';
import { PainelColaborador } from './painel-colaborador';

/*
 * O painel lateral de Colaboradores (teste de componente): cadastrar e editar uma pessoa.
 *
 * É uma janela modal: o teste confere o que um leitor de tela precisa (role="dialog", o título,
 * erros ligados aos campos) e o teclado (o foco ao abrir, Esc, o Tab preso dentro da janela).
 */
describe('PainelColaborador', () => {
  const ana: Colaborador = {
    id: 11,
    nome: 'Ana Lima',
    email: 'ana.lima@demo.crona',
    perfil: 'COLABORADOR',
    cargo: 'Desenvolvedora',
    dataAdmissao: '2024-01-08',
    ativo: true,
    departamento: { id: 1, nome: 'Engenharia' },
    gestor: { id: 2, nome: 'Roberto Neves' },
  };
  const roberto: Colaborador = { ...ana, id: 2, nome: 'Roberto Neves', perfil: 'GESTOR', gestor: null };
  const rafael: Colaborador = { ...ana, id: 1, nome: 'Rafael Costa', perfil: 'ADMIN', gestor: null };
  const departamentos: DepartamentoResumo[] = [
    { id: 1, nome: 'Engenharia', pessoas: 5, limiteFora: 1 },
    { id: 5, nome: 'RH', pessoas: 1, limiteFora: 1 },
  ];

  let api: Record<
    | 'gestores'
    | 'contar'
    | 'cadastrarColaborador'
    | 'alterarColaborador'
    | 'desativarColaborador'
    | 'reativarColaborador',
    ReturnType<typeof vi.fn>
  >;

  beforeEach(() => {
    api = {
      gestores: vi.fn(() => of([rafael, roberto])),
      contar: vi.fn(() => of(0)),
      cadastrarColaborador: vi.fn((d: DadosDoColaborador) => of({ ...ana, id: 99, nome: d.nome })),
      alterarColaborador: vi.fn((_id: number, d: DadosDoColaborador) => of({ ...ana, nome: d.nome })),
      desativarColaborador: vi.fn(() => of({ ...ana, ativo: false })),
      reativarColaborador: vi.fn(() => of(ana)),
    };
  });

  function erroHttp(status: number, corpo: object): Observable<never> {
    return throwError(() => new HttpErrorResponse({ status, error: corpo }));
  }

  /** Espera os setTimeout(0) do painel (o foco muda depois que o Angular desenha). */
  const esperar = () => new Promise((r) => setTimeout(r, 0));

  async function criar(colaborador: Colaborador | null = null) {
    TestBed.configureTestingModule({
      imports: [PainelColaborador],
      providers: [
        { provide: CadastrosApi, useValue: api },
        // Quem está logado é o Rafael (RH): o painel dele mesmo tem as travas do BUG-06.
        { provide: Sessao, useValue: { usuario: () => ({ id: 1, nome: 'Rafael Costa', perfil: 'ADMIN' }) } },
      ],
    });
    const fixture = TestBed.createComponent(PainelColaborador);
    fixture.componentRef.setInput('colaborador', colaborador);
    fixture.componentRef.setInput('departamentos', departamentos);
    fixture.detectChanges();
    await fixture.whenStable();
    const elemento = fixture.nativeElement as HTMLElement;

    const fechou = vi.fn();
    const salvou = vi.fn<(m: string) => void>();
    fixture.componentInstance.fechou.subscribe(fechou);
    fixture.componentInstance.salvou.subscribe(salvou);

    const botao = (nome: string) =>
      [...elemento.querySelectorAll('button')].find(
        (b) => b.getAttribute('aria-label') === nome || b.textContent?.trim() === nome,
      ) as HTMLButtonElement | undefined;
    const clicar = async (nome: string) => {
      botao(nome)!.click();
      fixture.detectChanges();
      await esperar();
    };
    /** O campo pelo texto do <label> em volta dele. */
    const campo = (rotulo: string) =>
      [...elemento.querySelectorAll('label.campo')]
        .find((l) => l.textContent?.trim().startsWith(rotulo))!
        .querySelector('input, select') as HTMLInputElement & HTMLSelectElement;
    const preencher = (rotulo: string, valor: string) => {
      const c = campo(rotulo);
      c.value = valor;
      c.dispatchEvent(new Event(c.tagName === 'SELECT' ? 'change' : 'input'));
      fixture.detectChanges();
    };
    const salvar = async () => {
      elemento.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
      await esperar();
    };
    const tecla = (key: string, shiftKey = false) => {
      elemento.dispatchEvent(new KeyboardEvent('keydown', { key, shiftKey, bubbles: true, cancelable: true }));
      fixture.detectChanges();
    };
    const legendas = () => [...elemento.querySelectorAll('legend')].map((l) => l.textContent?.trim());
    const erroGeral = () => elemento.querySelector('form [role="alert"]')?.textContent?.trim();

    return { fixture, elemento, fechou, salvou, botao, clicar, campo, preencher, salvar, tecla, legendas, erroGeral };
  }

  describe('a janela', () => {
    it('é um diálogo modal com o título como nome, e o foco começa no nome', async () => {
      const { elemento } = await criar();

      const janela = elemento.querySelector('[role="dialog"]')!;
      expect(janela.getAttribute('aria-modal')).toBe('true');
      expect(janela.getAttribute('aria-labelledby')).toBe('painel-titulo');
      expect(elemento.querySelector('#painel-titulo')?.textContent).toBe('Novo colaborador');
      expect(document.activeElement?.id).toBe('painel-nome');
    });

    it('Esc, o X, o Cancelar e o fundo escuro fecham', async () => {
      const { elemento, fechou, tecla, clicar } = await criar();

      tecla('Escape');
      await clicar('Fechar');
      await clicar('Cancelar');
      elemento.querySelector<HTMLElement>('.fundo')!.click();

      expect(fechou).toHaveBeenCalledTimes(4);
    });

    it('o Tab fica preso: do último volta ao primeiro, e Shift+Tab do primeiro vai ao último', async () => {
      const { elemento, tecla } = await criar();
      const focaveis = [
        ...elemento.querySelectorAll<HTMLElement>('[role="dialog"] button:not([disabled]), [role="dialog"] input, [role="dialog"] select'),
      ];
      const primeiro = focaveis[0];
      const ultimo = focaveis[focaveis.length - 1];

      ultimo.focus();
      tecla('Tab');
      expect(document.activeElement).toBe(primeiro);

      tecla('Tab', true);
      expect(document.activeElement).toBe(ultimo);
    });
  });

  describe('cadastrar', () => {
    it('as partes do formulário, com a senha inicial e sem "Acesso ao Crona"', async () => {
      const { legendas, botao } = await criar();

      expect(legendas()).toEqual(['Dados pessoais', 'Trabalho', 'Perfil de acesso', 'Primeiro acesso']);
      expect(botao('Cadastrar colaborador')).toBeDefined();
    });

    it('vazio: um erro em cada obrigatório, ligado ao campo, e o foco vai para o primeiro', async () => {
      const { salvar, campo, elemento } = await criar();
      (document.activeElement as HTMLElement).blur();

      await salvar();

      const invalidos = [...elemento.querySelectorAll('[aria-invalid="true"]')];
      expect(invalidos).toHaveLength(4); // nome, e-mail, data de admissão e senha
      expect(campo('Nome completo').getAttribute('aria-describedby')).toBe('erro-nome');
      expect(elemento.querySelector('#erro-nome')?.textContent).toBe('Informe o nome.');
      expect(document.activeElement).toBe(campo('Nome completo'));
      expect(api.cadastrarColaborador).not.toHaveBeenCalled();
    });

    it('corrigir um campo tira o erro só dele', async () => {
      const { salvar, preencher, elemento } = await criar();
      await salvar();

      preencher('Nome completo', 'Hana');

      expect(elemento.querySelector('#erro-nome')).toBeNull();
      expect(elemento.querySelector('#erro-email')).not.toBeNull();
    });

    it('com tudo certo: manda os dados limpos (com a senha) e avisa a lista', async () => {
      const { preencher, salvar, salvou, elemento } = await criar();

      preencher('Nome completo', '  Hana Teste ');
      preencher('E-mail de trabalho', ' hana@empresa.com ');
      preencher('Cargo', '   ');
      preencher('Departamento', '5');
      preencher('Gestor', '1');
      preencher('Data de admissão', '2024-01-08');
      preencher('Senha inicial', 'segredo123');
      elemento.querySelector<HTMLInputElement>('input[type="radio"][value="GESTOR"]')!.click();
      await salvar();

      expect(api.cadastrarColaborador).toHaveBeenCalledWith({
        nome: 'Hana Teste',
        email: 'hana@empresa.com',
        senha: 'segredo123',
        perfil: 'GESTOR',
        cargo: null,
        dataAdmissao: '2024-01-08',
        departamentoId: 5,
        gestorId: 1,
      });
      expect(salvou).toHaveBeenCalledWith('Hana Teste cadastrado.');
    });

    it('e-mail já usado (409): o erro aparece no campo de e-mail', async () => {
      api.cadastrarColaborador.mockReturnValue(
        erroHttp(409, { detail: 'O e-mail ana.lima@demo.crona já está em uso.' }),
      );
      const { preencher, salvar, elemento, salvou } = await criar();
      preencher('Nome completo', 'Ana 2');
      preencher('E-mail de trabalho', 'ana.lima@demo.crona');
      preencher('Data de admissão', '2024-01-08');
      preencher('Senha inicial', 'segredo123');

      await salvar();

      expect(elemento.querySelector('#erro-email')?.textContent).toBe(
        'O e-mail ana.lima@demo.crona já está em uso.',
      );
      expect(salvou).not.toHaveBeenCalled();
    });
  });

  describe('editar', () => {
    it('vem preenchido, sem a senha, com "Acesso ao Crona" e "Salvar alterações"', async () => {
      const { campo, legendas, botao, elemento } = await criar(ana);

      expect(elemento.querySelector('#painel-titulo')?.textContent).toBe('Ana Lima');
      expect(campo('Nome completo').value).toBe('Ana Lima');
      expect(campo('E-mail de trabalho').value).toBe('ana.lima@demo.crona');
      expect(campo('Departamento').value).toBe('1');
      expect(campo('Gestor').value).toBe('2');
      expect(legendas()).toEqual(['Dados pessoais', 'Trabalho', 'Perfil de acesso', 'Acesso ao Crona']);
      expect(botao('Salvar alterações')).toBeDefined();
    });

    it('salvar manda os dados sem a senha e avisa a lista', async () => {
      const { preencher, salvar, salvou } = await criar(ana);

      preencher('Cargo', 'Tech lead');
      await salvar();

      expect(api.alterarColaborador).toHaveBeenCalledWith(11, {
        nome: 'Ana Lima',
        email: 'ana.lima@demo.crona',
        perfil: 'COLABORADOR',
        cargo: 'Tech lead',
        dataAdmissao: '2024-01-08',
        departamentoId: 1,
        gestorId: 2,
      });
      expect(salvou).toHaveBeenCalledWith('Dados de Ana Lima salvos.');
    });

    it('a própria pessoa não aparece na lista de gestores dela', async () => {
      const { campo } = await criar(roberto);

      const opcoes = [...campo('Gestor').querySelectorAll('option')].map((o) => o.textContent?.trim());
      expect(opcoes).toEqual(['Sem gestor', 'Rafael Costa']);
    });

    it('uma regra da API (422) aparece como frase geral, num alerta', async () => {
      api.alterarColaborador.mockReturnValue(
        erroHttp(422, { detail: 'Esse gestor faz parte da equipe de Ana Lima.' }),
      );
      const { salvar, erroGeral } = await criar(ana);

      await salvar();

      expect(erroGeral()).toBe('Esse gestor faz parte da equipe de Ana Lima.');
    });
  });

  describe('acesso ao Crona', () => {
    it('pergunta à API quantas pessoas ATIVAS a pessoa tem na equipe', async () => {
      await criar(roberto);

      expect(api.contar).toHaveBeenCalledWith({ gestorId: 2, ativo: true });
    });

    it('com equipe: no lugar do botão, o aviso do que fazer', async () => {
      api.contar.mockReturnValue(of(5));
      const { elemento, botao } = await criar(roberto);

      expect(elemento.querySelector('[role="note"]')?.textContent?.trim()).toBe(
        'Roberto tem 5 pessoas na equipe. Para desativar, passe essas pessoas para outro gestor antes.',
      );
      expect(botao('Desativar acesso de Roberto')).toBeUndefined();
    });

    it('sem equipe: confirma antes, com o foco no "Voltar"; "Voltar" devolve o foco ao botão', async () => {
      const { clicar, elemento } = await criar(ana);

      await clicar('Desativar acesso de Ana');
      expect(elemento.querySelector('.confirmar')?.textContent).toContain('Ana deixa de entrar no Crona.');
      expect(document.activeElement?.id).toBe('painel-voltar');
      expect(api.desativarColaborador).not.toHaveBeenCalled();

      await clicar('Voltar');
      expect(document.activeElement?.id).toBe('painel-desativar');
    });

    it('confirmar desativa e avisa a lista', async () => {
      const { clicar, salvou } = await criar(ana);
      await clicar('Desativar acesso de Ana');

      await clicar('Desativar acesso');

      expect(api.desativarColaborador).toHaveBeenCalledWith(11);
      expect(salvou).toHaveBeenCalledWith('Acesso de Ana Lima desativado.');
    });

    it('a API recusa desativar (422): fecha a confirmação e mostra o motivo', async () => {
      api.desativarColaborador.mockReturnValue(
        erroHttp(422, { detail: 'Ana Lima é gestor de outras pessoas. Transfira a equipe antes de desativar.' }),
      );
      const { clicar, elemento, erroGeral } = await criar(ana);
      await clicar('Desativar acesso de Ana');

      await clicar('Desativar acesso');

      expect(elemento.querySelector('.confirmar')).toBeNull();
      expect(erroGeral()).toBe('Ana Lima é gestor de outras pessoas. Transfira a equipe antes de desativar.');
    });

    /*
     * BUG-06: o RH não tranca a si mesmo para fora. A API recusa (ColaboradorApiTest), e a tela nem
     * oferece: no painel de quem está logado, não há "Desativar" e o perfil fica travado.
     */
    describe('BUG-06: o painel de quem está logado', () => {
      const perfil = (elemento: HTMLElement) =>
        [...elemento.querySelectorAll('fieldset')].find((f) => f.querySelector('legend')?.textContent?.trim() === 'Perfil de acesso')!;

      it('não oferece desativar: explica que outra pessoa do RH precisa fazer isso', async () => {
        const { elemento, botao } = await criar(rafael);

        expect(botao('Desativar acesso de Rafael')).toBeUndefined();
        expect(elemento.textContent).toContain('Este é o seu acesso. Para desativá-lo, peça para outra pessoa do RH.');
      });

      it('o perfil fica travado, com a dica ligada ao grupo', async () => {
        const { elemento } = await criar(rafael);

        const grupo = perfil(elemento);
        expect(grupo.disabled).toBe(true);
        expect(grupo.getAttribute('aria-describedby')).toBe('dica-proprio-perfil');
        expect(elemento.querySelector('#dica-proprio-perfil')?.textContent).toBe(
          'Você não pode mudar o próprio perfil. Peça para outra pessoa do RH.',
        );
      });

      it('os próprios dados continuam editáveis: salvar manda o perfil de sempre', async () => {
        const { preencher, salvar, salvou } = await criar(rafael);

        preencher('Cargo', 'Coordenador de RH');
        await salvar();

        expect(api.alterarColaborador).toHaveBeenCalledWith(1, expect.objectContaining({
          perfil: 'ADMIN',
          cargo: 'Coordenador de RH',
        }));
        expect(salvou).toHaveBeenCalledWith('Dados de Rafael Costa salvos.');
      });

      it('guarda: no painel de OUTRA pessoa, o perfil fica livre e o "Desativar" aparece', async () => {
        const { elemento, botao } = await criar(ana);

        expect(perfil(elemento).disabled).toBe(false);
        expect(elemento.querySelector('#dica-proprio-perfil')).toBeNull();
        expect(botao('Desativar acesso de Ana')).toBeDefined();
      });
    });

    it('inativo: explica e oferece "Reativar acesso" (sem perguntar pela equipe)', async () => {
      const { clicar, elemento, salvou } = await criar({ ...ana, ativo: false });

      expect(elemento.textContent).toContain('Ana não entra no Crona agora.');
      expect(api.contar).not.toHaveBeenCalled();
      await clicar('Reativar acesso');

      expect(api.reativarColaborador).toHaveBeenCalledWith(11);
      expect(salvou).toHaveBeenCalledWith('Acesso de Ana Lima reativado.');
    });
  });
});
