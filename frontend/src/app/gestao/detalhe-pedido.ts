import { Component, computed, effect, input, output, signal, untracked } from '@angular/core';

import { Avatar } from '../compartilhado/avatar';
import { Checklist } from '../compartilhado/checklist';
import { Icone } from '../compartilhado/icone';
import { diaMes, diaMesAno, haQuanto } from '../nucleo/datas';
import { Decisao, DetalheDaAprovacao, Verificacao } from '../nucleo/modelos';
import { acaoPrincipal, linhaDoTempo } from './aprovacoes.regras';

export interface ErroDaDecisao {
  mensagem: string;
  verificacoes: Verificacao[];
}

/**
 * O detalhe de um pedido na tela Aprovações (lado direito do redesign).
 * Mostra tudo o que o gestor precisa para decidir e devolve a decisão para a página.
 */
@Component({
  selector: 'crona-detalhe-pedido',
  imports: [Avatar, Checklist, Icone],
  templateUrl: './detalhe-pedido.html',
  styleUrl: './detalhe-pedido.css',
})
export class DetalhePedido {
  readonly detalhe = input.required<DetalheDaAprovacao>();
  readonly ocupado = input(false);
  readonly erro = input<ErroDaDecisao | null>(null);

  readonly aprovou = output<Decisao>();
  readonly recusou = output<string>();

  protected readonly mensagem = signal('');
  /** Erro de preenchimento (ex.: recusar sem motivo), antes de ir para a API. */
  protected readonly aviso = signal<string | null>(null);

  protected readonly pedido = computed(() => this.detalhe().pedido);
  protected readonly primeiroNome = computed(() => this.pedido().colaborador.nome.split(' ')[0]);
  protected readonly acao = computed(() => acaoPrincipal(this.pedido().situacao));
  protected readonly saldoDepois = computed(
    () => this.detalhe().livresNoPeriodo - this.pedido().diasGozo - this.pedido().diasAbono,
  );
  protected readonly alertas = computed(() => this.detalhe().verificacoes.filter((v) => v.resultado !== 'OK'));
  protected readonly regrasOk = computed(() => this.detalhe().verificacoes.filter((v) => v.resultado === 'OK'));
  protected readonly linha = computed(() =>
    linhaDoTempo(
      { nome: this.pedido().colaborador.nome, inicio: this.pedido().inicio, fim: this.pedido().fim },
      this.detalhe().colegasNoPeriodo,
      this.detalhe().limiteDoDepartamento,
    ),
  );

  protected readonly diaMes = diaMes;
  protected readonly diaMesAno = diaMesAno;
  protected readonly haQuanto = haQuanto;

  constructor() {
    // Outro pedido escolhido na lista (ou o mesmo recarregado): começa com a mensagem em branco.
    effect(() => {
      this.detalhe();
      untracked(() => {
        this.mensagem.set('');
        this.aviso.set(null);
      });
    });
  }

  protected aprovar(): void {
    const mensagem = this.mensagem().trim();
    if (this.acao() === 'aprovar-mesmo-assim' && !mensagem) {
      this.aviso.set('Para aprovar mesmo assim, escreva o motivo: ele fica registrado.');
      return;
    }
    this.aviso.set(null);
    this.aprovou.emit({ mesmoAssim: this.acao() === 'aprovar-mesmo-assim', mensagem: mensagem || undefined });
  }

  protected recusar(): void {
    const mensagem = this.mensagem().trim();
    if (!mensagem) {
      this.aviso.set(`Para recusar, escreva o motivo: ${this.primeiroNome()} vai ver a mensagem.`);
      return;
    }
    this.aviso.set(null);
    this.recusou.emit(mensagem);
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLTextAreaElement).value;
  }

  /** "2027-03-05" → 5 */
  protected numeroDoDia(iso: string): number {
    return Number(iso.slice(8, 10));
  }

  /** A coluna do CSS grid de cada barra da linha do tempo (a 1ª coluna é a dos nomes). */
  protected colunas(de: number, ate: number): string {
    return `${de + 2} / ${ate + 3}`;
  }
}
