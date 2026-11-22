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

  it('fora da demonstração, não mostra nenhuma conta', () => {
    const { elemento } = criar(false);

    expect(elemento.querySelector('.demonstracao')).toBeNull();
    expect(elemento.textContent).not.toContain('crona123');
  });
});
