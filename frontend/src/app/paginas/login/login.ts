import { HttpErrorResponse } from '@angular/common/http';
import { Component, inject, signal } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { Router } from '@angular/router';

import { Icone } from '../../compartilhado/icone';
import { Logo } from '../../compartilhado/logo';
import {
  CONTAS_DE_DEMONSTRACAO,
  ContaDeDemonstracao,
  DEMONSTRACAO,
  SENHA_DE_DEMONSTRACAO,
} from '../../nucleo/demonstracao';
import { Problema } from '../../nucleo/modelos';
import { Sessao } from '../../nucleo/sessao';

/**
 * Tela de login. Usa um formulário REATIVO: os campos e as validações ficam no TypeScript
 * (o FormGroup abaixo), e o HTML só liga cada <input> ao seu campo com formControlName.
 */
@Component({
  selector: 'crona-login',
  imports: [ReactiveFormsModule, Logo, Icone],
  templateUrl: './login.html',
  styleUrl: './login.css',
})
export class Login {
  private readonly sessao = inject(Sessao);
  private readonly router = inject(Router);

  protected readonly formulario = inject(FormBuilder).nonNullable.group({
    email: ['', [Validators.required, Validators.email]],
    senha: ['', Validators.required],
  });

  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);
  /** No `npm start` e na versão de demonstração: mostra as contas de exemplo. */
  protected readonly demonstracao = inject(DEMONSTRACAO);
  protected readonly contas = CONTAS_DE_DEMONSTRACAO;
  protected readonly senhaDeDemonstracao = SENHA_DE_DEMONSTRACAO;

  constructor() {
    if (this.sessao.token()) {
      this.router.navigate(['/inicio']); // já está logado
    }
  }

  protected entrar(): void {
    if (this.formulario.invalid) {
      this.formulario.markAllAsTouched(); // mostra os erros dos campos
      return;
    }
    const { email, senha } = this.formulario.getRawValue();
    this.enviando.set(true);
    this.erro.set(null);

    this.sessao.entrar(email.trim(), senha).subscribe({
      next: () => this.router.navigate(['/inicio']),
      error: (resposta: HttpErrorResponse) => {
        this.enviando.set(false);
        this.erro.set(mensagemDeErro(resposta));
      },
    });
  }

  /** Um clique numa conta de exemplo: preenche o formulário e já entra. */
  protected entrarComo(conta: ContaDeDemonstracao): void {
    this.formulario.setValue({ email: conta.email, senha: SENHA_DE_DEMONSTRACAO });
    this.entrar();
  }

  protected campoInvalido(nome: 'email' | 'senha'): boolean {
    const campo = this.formulario.controls[nome];
    return campo.invalid && campo.touched;
  }
}

function mensagemDeErro(resposta: HttpErrorResponse): string {
  if (resposta.status === 0 || resposta.status >= 500) {
    return 'Não foi possível falar com o servidor. A API está rodando?';
  }
  return (resposta.error as Problema | null)?.detail ?? 'Não foi possível entrar.';
}
