import { TestBed } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { of } from 'rxjs';

import { DEMONSTRACAO } from '../../nucleo/demonstracao';
import { Sessao } from '../../nucleo/sessao';
import { Login } from './login';

/*
 * Tela de login: as contas de exemplo só aparecem no modo de demonstração. O token
 * DEMONSTRACAO é trocado aqui para testar os dois jeitos, e a Sessao é falsa (sem API).
 */
describe('Login', () => {
  const sessao = {
    token: vi.fn(() => null),
    entrar: vi.fn(() => of({})),
  };

  function criar(demonstracao: boolean) {
    TestBed.configureTestingModule({
      imports: [Login],
      providers: [
        provideRouter([]),
        { provide: Sessao, useValue: sessao },
        { provide: DEMONSTRACAO, useValue: demonstracao },
      ],
    });
    vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    const fixture = TestBed.createComponent(Login);
    fixture.detectChanges();
    return { fixture, elemento: fixture.nativeElement as HTMLElement };
  }

  beforeEach(() => vi.clearAllMocks());

  it('na demonstração, mostra as 3 contas de exemplo e a senha', () => {
    const { elemento } = criar(true);

    const contas = elemento.querySelectorAll('.conta');
    expect(contas).toHaveLength(3);
    expect(contas[1].textContent).toContain('Roberto Neves');
    expect(contas[1].textContent).toContain('Gestor');
    expect(elemento.querySelector('.demonstracao')?.textContent).toContain('crona123');
  });

  it('um clique numa conta de exemplo entra com ela', () => {
    const { elemento } = criar(true);

    (elemento.querySelectorAll('.conta')[1] as HTMLButtonElement).click();

    expect(sessao.entrar).toHaveBeenCalledWith('roberto.neves@demo.crona', 'crona123');
    expect(TestBed.inject(Router).navigate).toHaveBeenCalledWith(['/inicio']);
  });

  /*
   * P2 (campos): o erro de cada campo fica LIGADO ao campo (aria-invalid + aria-describedby), para o
   * leitor de tela dizer "inválido" e ler o motivo quando a pessoa volta ao campo.
   */
  describe('erros dos campos', () => {
    const enviar = (fixture: { nativeElement: HTMLElement; detectChanges: () => void }) => {
      fixture.nativeElement.querySelector('form')!.dispatchEvent(new Event('submit'));
      fixture.detectChanges();
    };

    it('enviar vazio: os dois campos ficam inválidos, cada um ligado à sua mensagem, e não tenta entrar', () => {
      const { fixture, elemento } = criar(false);

      enviar(fixture);

      const email = elemento.querySelector<HTMLInputElement>('input[type="email"]')!;
      const senha = elemento.querySelector<HTMLInputElement>('input[type="password"]')!;
      expect(email.getAttribute('aria-invalid')).toBe('true');
      expect(email.getAttribute('aria-describedby')).toBe('erro-email');
      expect(elemento.querySelector('#erro-email.campo-erro')?.textContent).toBe('Informe um e-mail válido.');
      expect(senha.getAttribute('aria-invalid')).toBe('true');
      expect(senha.getAttribute('aria-describedby')).toBe('erro-senha');
      expect(elemento.querySelector('#erro-senha.campo-erro')?.textContent).toBe('Informe a senha.');
      expect(sessao.entrar).not.toHaveBeenCalled();

      // Sem leitura dupla: o NOME é só o rótulo (aria-labelledby); o erro vem só na descrição.
      const rotulo = (campo: HTMLInputElement) =>
        document.getElementById(campo.getAttribute('aria-labelledby')!)?.textContent?.trim();
      expect(rotulo(email)).toBe('E-mail');
      expect(rotulo(senha)).toBe('Senha');
      expect(email.closest('label')).not.toBeNull(); // clicar no texto ainda foca o campo
      expect(senha.closest('label')).not.toBeNull();
    });

    it('antes de tentar, nenhum campo aparece como inválido', () => {
      const { elemento } = criar(false);

      expect(elemento.querySelectorAll('[aria-invalid]')).toHaveLength(0);
      expect(elemento.querySelector('.campo-erro')).toBeNull();
    });

    it('corrigir o e-mail tira o erro só dele', () => {
      const { fixture, elemento } = criar(false);
      enviar(fixture);

      const email = elemento.querySelector<HTMLInputElement>('input[type="email"]')!;
      email.value = 'ana.lima@demo.crona';
      email.dispatchEvent(new Event('input'));
      fixture.detectChanges();

      expect(email.getAttribute('aria-invalid')).toBeNull();
      expect(email.getAttribute('aria-describedby')).toBeNull();
      expect(elemento.querySelector('#erro-email')).toBeNull();
      expect(elemento.querySelector('#erro-senha')).not.toBeNull();
    });
  });

  it('fora da demonstração, não mostra nenhuma conta', () => {
    const { elemento } = criar(false);

    expect(elemento.querySelector('.demonstracao')).toBeNull();
    expect(elemento.textContent).not.toContain('crona123');
  });
});
