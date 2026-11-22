import { Component, computed, ElementRef, inject, signal } from '@angular/core';
import { Router } from '@angular/router';

import { Icone } from '../compartilhado/icone';
import { Pendencias } from '../ferias/pendencias';
import { haQuanto } from '../nucleo/datas';
import { Notificacao } from '../nucleo/modelos';

/**
 * O sino do menu (Fase 8b, PAD-03): o número de não lidas e, ao clicar, a lista.
 * O estado (as notificações) fica no serviço Pendencias; o componente só mostra e reage.
 */
@Component({
  selector: 'crona-sino',
  imports: [Icone],
  templateUrl: './sino.html',
  styleUrl: './sino.css',
  host: {
    // Escuta cliques no documento inteiro e a tecla Esc, para fechar o painel.
    '(document:click)': 'aoClicarFora($event)',
    '(document:keydown.escape)': 'aberto.set(false)',
  },
})
export class Sino {
  private readonly pendencias = inject(Pendencias);
  private readonly router = inject(Router);
  private readonly elemento = inject(ElementRef<HTMLElement>);

  protected readonly aberto = signal(false);
  protected readonly notificacoes = this.pendencias.notificacoes;
  protected readonly naoLidas = computed(() => this.notificacoes().naoLidas);
  protected readonly haQuanto = haQuanto;

  protected alternar(): void {
    this.aberto.update((aberto) => !aberto);
    if (this.aberto()) {
      this.pendencias.atualizar(); // ao abrir, busca as mais recentes
    }
  }

  protected abrir(notificacao: Notificacao): void {
    this.pendencias.marcarComoLida(notificacao.id);
    this.aberto.set(false);
    if (notificacao.link) {
      this.router.navigateByUrl(notificacao.link);
    }
  }

  protected marcarTodas(): void {
    this.pendencias.marcarTodasComoLidas();
  }

  protected aoClicarFora(evento: MouseEvent): void {
    if (this.aberto() && !this.elemento.nativeElement.contains(evento.target as Node)) {
      this.aberto.set(false);
    }
  }
}
