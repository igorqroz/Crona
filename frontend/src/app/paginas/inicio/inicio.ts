import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Avatar } from '../../compartilhado/avatar';
import { Icone } from '../../compartilhado/icone';
import { FeriasApi } from '../../ferias/ferias-api';
import { dataPorExtenso, diaMesAno, haQuanto, intervalo } from '../../nucleo/datas';
import { FeriasDoColega, PeriodoAquisitivo, Problema, Solicitacao } from '../../nucleo/modelos';
import { Sessao } from '../../nucleo/sessao';
import { blocos, passosDoPedido, pedidoEmFoco, periodoEmFoco, PropostaParaMim, propostaParaMim, saldo } from './inicio.regras';

/**
 * A tela Início do colaborador (tela 3 do redesign):
 * o saldo, o andamento do pedido, a proposta de troca que espera a minha resposta e
 * as férias marcadas na minha equipe.
 *
 * O estado da tela fica em SIGNALS. Os "computed" se recalculam sozinhos quando os signals
 * de que dependem mudam, e o template se atualiza junto.
 */
@Component({
  selector: 'crona-inicio',
  imports: [RouterLink, Icone, Avatar],
  templateUrl: './inicio.html',
  styleUrl: './inicio.css',
})
export class Inicio {
  private readonly api = inject(FeriasApi);
  private readonly sessao = inject(Sessao);

  protected readonly hoje = dataPorExtenso(new Date());
  protected readonly primeiroNome = computed(() => this.sessao.usuario()?.nome.split(' ')[0] ?? '');

  protected readonly estado = signal<'carregando' | 'pronto' | 'erro'>('carregando');
  protected readonly periodo = signal<PeriodoAquisitivo | null>(null);
  protected readonly pedido = signal<Solicitacao | null>(null);
  protected readonly proposta = signal<PropostaParaMim | null>(null);
  protected readonly equipe = signal<FeriasDoColega[]>([]);

  protected readonly saldo = computed(() => {
    const periodo = this.periodo();
    return periodo ? saldo(periodo) : 0;
  });
  protected readonly blocos = computed(() => {
    const periodo = this.periodo();
    return periodo ? blocos(periodo) : [];
  });
  protected readonly passos = computed(() => {
    const pedido = this.pedido();
    return pedido ? passosDoPedido(pedido) : [];
  });

  /** Enquanto aceita ou recusa uma troca (para desabilitar os botões). */
  protected readonly respondendo = signal(false);
  protected readonly erroDaTroca = signal<string | null>(null);

  // Os ajudantes de data, para o template poder usar.
  protected readonly intervalo = intervalo;
  protected readonly diaMesAno = diaMesAno;
  protected readonly haQuanto = haQuanto;

  constructor() {
    this.carregar();
  }

  /** Busca as 4 informações da tela AO MESMO TEMPO (forkJoin) e espera todas chegarem. */
  protected carregar(): void {
    const meuId = this.sessao.usuario()?.id ?? 0;
    const hoje = new Date().toLocaleDateString('sv-SE'); // "2026-11-21", no fuso do navegador

    forkJoin({
      periodos: this.api.periodos(),
      solicitacoes: this.api.solicitacoes(),
      negociacoes: this.api.negociacoes(),
      equipe: this.api.feriasDaEquipe(),
    }).subscribe({
      next: (dados) => {
        this.periodo.set(periodoEmFoco(dados.periodos));
        this.pedido.set(pedidoEmFoco(dados.solicitacoes, hoje));
        this.proposta.set(propostaParaMim(dados.negociacoes, meuId));
        this.equipe.set(dados.equipe);
        this.estado.set('pronto');
      },
      error: () => this.estado.set('erro'),
    });
  }

  protected aceitarTroca(id: number): void {
    this.responder(this.api.aceitarTroca(id));
  }

  protected recusarTroca(id: number): void {
    this.responder(this.api.recusarTroca(id));
  }

  private responder(acao: ReturnType<FeriasApi['aceitarTroca']>): void {
    this.respondendo.set(true);
    this.erroDaTroca.set(null);
    acao.subscribe({
      next: () => {
        this.respondendo.set(false);
        this.carregar(); // a tela inteira muda (a proposta some, o saldo pode mudar)
      },
      error: (resposta: HttpErrorResponse) => {
        this.respondendo.set(false);
        this.erroDaTroca.set((resposta.error as Problema | null)?.detail ?? 'Não foi possível responder à troca.');
      },
    });
  }
}
