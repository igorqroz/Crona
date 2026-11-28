import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin } from 'rxjs';

import { Icone } from '../compartilhado/icone';
import { DepartamentoResumo, Problema, RegrasDaEmpresa, RegrasDaEmpresaComContexto } from '../nucleo/modelos';
import { CadastrosApi } from './cadastros-api';
import { departamentosVazios, ErrosDasRegras, mudou, previaDosDepartamentos, primeiroDiaPossivel, validarRegras } from './regras.regras';

/**
 * Cadastros > Regras de férias (só o RH). As regras da empresa (EMP-01, 03, 04 e 06), uma por cartão,
 * com uma prévia ao vivo do efeito de cada uma. Tudo é salvo de uma vez (um PUT só), pela barra
 * "Alterações não salvas", que só aparece quando algo mudou.
 */
@Component({
  selector: 'crona-regras',
  imports: [Icone],
  templateUrl: './regras.html',
  styleUrl: './regras.css',
})
export class Regras {
  private readonly api = inject(CadastrosApi);

  protected readonly estado = signal<'carregando' | 'pronto' | 'erro'>('carregando');
  /** O que está gravado na API (com os dois números de contexto). */
  protected readonly salvas = signal<RegrasDaEmpresaComContexto | null>(null);
  protected readonly departamentos = signal<DepartamentoResumo[]>([]);

  // ------------------------------------------------------------------ o que está na tela
  protected readonly antecedencia = signal(30);
  protected readonly percentualLigado = signal(false);
  protected readonly percentual = signal(70);
  protected readonly limiteLigado = signal(false);
  protected readonly limite = signal(1);
  protected readonly rhLigado = signal(false);

  protected readonly erros = signal<ErrosDasRegras>({});
  protected readonly ocupado = signal(false);
  protected readonly feito = signal<string | null>(null);
  protected readonly erroDaApi = signal<string | null>(null);

  /** As regras como ficariam se salvasse agora (um campo desligado vira nulo, mas o valor fica guardado). */
  protected readonly atual = computed<RegrasDaEmpresa>(() => ({
    antecedenciaMinimaDias: this.antecedencia(),
    percentualMinimoTrabalhando: this.percentualLigado() ? this.percentual() : null,
    limiteFeriasSimultaneasEmpresa: this.limiteLigado() ? this.limite() : null,
    exigeConfirmacaoRh: this.rhLigado(),
  }));

  protected readonly alterado = computed(() => {
    const salvas = this.salvas();
    return salvas !== null && mudou(this.atual(), salvas);
  });

  // ------------------------------------------------------------------ prévias
  protected readonly primeiroDia = computed(() =>
    this.antecedencia() >= 30 ? primeiroDiaPossivel(this.antecedencia()) : null,
  );
  protected readonly hoje = new Date().toLocaleDateString('pt-BR', { day: '2-digit', month: '2-digit' });
  protected readonly previa = computed(() =>
    this.percentual() >= 0 && this.percentual() <= 100 ? previaDosDepartamentos(this.departamentos(), this.percentual()) : [],
  );
  protected readonly vazios = computed(() => departamentosVazios(this.departamentos()));

  /** EMP-06: desligando agora, com pedidos ainda na fila do RH. */
  protected readonly pedidosNaFilaDoRh = computed(() => {
    const salvas = this.salvas();
    return salvas !== null && salvas.exigeConfirmacaoRh && !this.rhLigado() ? salvas.pedidosAguardandoRh : 0;
  });

  constructor() {
    this.carregar();
  }

  protected carregar(): void {
    this.estado.set('carregando');
    forkJoin({ regras: this.api.regras(), departamentos: this.api.departamentos() }).subscribe({
      next: ({ regras, departamentos }) => {
        this.departamentos.set(departamentos);
        this.aplicar(regras);
        this.estado.set('pronto');
      },
      error: () => this.estado.set('erro'),
    });
  }

  /** Põe na tela o que veio da API. Os campos desligados ficam com um valor de partida. */
  private aplicar(regras: RegrasDaEmpresaComContexto): void {
    this.salvas.set(regras);
    this.antecedencia.set(regras.antecedenciaMinimaDias);
    this.percentualLigado.set(regras.percentualMinimoTrabalhando !== null);
    if (regras.percentualMinimoTrabalhando !== null) {
      this.percentual.set(regras.percentualMinimoTrabalhando);
    }
    this.limiteLigado.set(regras.limiteFeriasSimultaneasEmpresa !== null);
    if (regras.limiteFeriasSimultaneasEmpresa !== null) {
      this.limite.set(regras.limiteFeriasSimultaneasEmpresa);
    }
    this.rhLigado.set(regras.exigeConfirmacaoRh);
    this.erros.set({});
  }

  // ------------------------------------------------------------------ campos

  /** Um número digitado (vazio ou inválido vira NaN, e a validação acusa). */
  protected numero(evento: Event): number {
    const texto = (evento.target as HTMLInputElement).value.trim();
    return texto === '' ? Number.NaN : Number(texto);
  }

  protected mudouCampo(campo: keyof ErrosDasRegras, alterar: () => void): void {
    alterar();
    this.feito.set(null);
    if (this.erros()[campo]) {
      this.erros.update(({ [campo]: _, ...resto }) => resto);
    }
  }

  protected alternar(chave: 'percentual' | 'limite' | 'rh'): void {
    this.feito.set(null);
    if (chave === 'percentual') {
      this.percentualLigado.update((v) => !v);
      this.erros.update(({ percentual: _, ...resto }) => resto);
    } else if (chave === 'limite') {
      this.limiteLigado.update((v) => !v);
      this.erros.update(({ limite: _, ...resto }) => resto);
    } else {
      this.rhLigado.update((v) => !v);
    }
  }

  // ------------------------------------------------------------------ salvar e descartar

  protected descartar(): void {
    const salvas = this.salvas();
    if (salvas) {
      this.aplicar(salvas);
    }
    this.erroDaApi.set(null);
  }

  protected salvar(): void {
    const erros = validarRegras(this.atual());
    this.erros.set(erros);
    this.erroDaApi.set(null);
    if (Object.keys(erros).length > 0) {
      // O foco vai para o primeiro campo com erro.
      setTimeout(() => document.querySelector<HTMLElement>('crona-regras [aria-invalid="true"]')?.focus());
      return;
    }
    this.ocupado.set(true);
    this.api.salvarRegras(this.atual()).subscribe({
      next: (regras) => {
        this.ocupado.set(false);
        this.aplicar(regras);
        this.feito.set('Regras salvas. Valem para os próximos pedidos.');
        setTimeout(() => document.getElementById('feito')?.focus());
      },
      error: (resposta: HttpErrorResponse) => {
        this.ocupado.set(false);
        const problema = resposta.error as Problema | null;
        this.erroDaApi.set(problema?.detail ?? 'Não foi possível salvar as regras. Tente de novo.');
      },
    });
  }
}
