import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin, Observable } from 'rxjs';

import { Avatar } from '../../compartilhado/avatar';
import { Checklist } from '../../compartilhado/checklist';
import { FeriasApi } from '../../ferias/ferias-api';
import { Pendencias } from '../../ferias/pendencias';
import { intervalo } from '../../nucleo/datas';
import { Contraproposta, FeriasDoColega, Problema, PropostaDeTroca, Solicitacao } from '../../nucleo/modelos';
import { Sessao } from '../../nucleo/sessao';
import { CartaoTroca, ErroDaAcao } from './cartao-troca';
import { feriasQuePossoOferecer, ordenar, visaoDaTroca, VisaoDaTroca } from './trocas.regras';

/**
 * Tela "Trocas de período" (o redesign não desenhou: usa os mesmos componentes e tokens).
 *
 * - À esquerda, as minhas negociações (as que propus e as que recebi), cada uma num cartão.
 * - À direita, as férias aprovadas da equipe: escolher uma abre o formulário de proposta.
 */
@Component({
  selector: 'crona-trocas',
  imports: [Avatar, Checklist, CartaoTroca],
  templateUrl: './trocas.html',
  styleUrl: './trocas.css',
})
export class Trocas {
  private readonly api = inject(FeriasApi);
  private readonly sessao = inject(Sessao);
  private readonly pendencias = inject(Pendencias);

  protected readonly estado = signal<'carregando' | 'pronto' | 'erro'>('carregando');
  protected readonly trocas = signal<VisaoDaTroca[]>([]);
  protected readonly equipe = signal<FeriasDoColega[]>([]);
  protected readonly minhasFerias = signal<Solicitacao[]>([]);

  /** Qual cartão está com uma ação indo para a API, e o erro da última ação (por cartão). */
  protected readonly ocupado = signal<number | null>(null);
  protected readonly erroDoCartao = signal<{ id: number; erro: ErroDaAcao } | null>(null);

  // ------------------------------------------------------------------ formulário de proposta
  protected readonly alvo = signal<FeriasDoColega | null>(null);
  protected readonly oferecida = signal<number | null>(null);
  protected readonly meuInicio = signal('');
  protected readonly meusDias = signal(15);
  protected readonly inicioDoColega = signal('');
  protected readonly mensagem = signal('');
  protected readonly enviando = signal(false);
  protected readonly erroDaProposta = signal<ErroDaAcao | null>(null);

  protected readonly podePropor = computed(
    () => !!this.alvo() && !!this.meuInicio() && !!this.inicioDoColega() && !this.enviando(),
  );

  protected readonly intervalo = intervalo;
  protected readonly amanha = amanhaIso();

  constructor() {
    this.carregar();
  }

  protected carregar(): void {
    const meuId = this.sessao.usuario()?.id ?? 0;
    forkJoin({
      negociacoes: this.api.negociacoes(),
      equipe: this.api.feriasDaEquipe(),
      solicitacoes: this.api.solicitacoes(),
    }).subscribe({
      next: (dados) => {
        this.trocas.set(ordenar(dados.negociacoes.map((n) => visaoDaTroca(n, meuId))));
        this.equipe.set(dados.equipe);
        this.minhasFerias.set(feriasQuePossoOferecer(dados.solicitacoes, new Date().toLocaleDateString('sv-SE')));
        this.estado.set('pronto');
        this.pendencias.atualizar(); // o número no menu acompanha a tela
      },
      error: () => this.estado.set('erro'),
    });
  }

  // ------------------------------------------------------------------ respostas (cartões)

  protected aceitar(id: number): void {
    this.executar(id, this.api.aceitarTroca(id));
  }

  protected recusar(id: number, mensagem?: string): void {
    this.executar(id, this.api.recusarTroca(id, mensagem));
  }

  protected cancelar(id: number): void {
    this.executar(id, this.api.cancelarTroca(id));
  }

  protected contrapropor(id: number, contraproposta: Contraproposta): void {
    this.executar(id, this.api.contrapropor(id, contraproposta));
  }

  /** Toda ação de cartão segue o mesmo roteiro: marca ocupado, chama a API, recarrega ou mostra o erro. */
  private executar(id: number, acao: Observable<unknown>): void {
    this.ocupado.set(id);
    this.erroDoCartao.set(null);
    acao.subscribe({
      next: () => {
        this.ocupado.set(null);
        this.carregar();
      },
      error: (resposta: HttpErrorResponse) => {
        this.ocupado.set(null);
        this.erroDoCartao.set({ id, erro: erroDaApi(resposta) });
      },
    });
  }

  protected erroDe(id: number): ErroDaAcao | null {
    const erro = this.erroDoCartao();
    return erro?.id === id ? erro.erro : null;
  }

  // ------------------------------------------------------------------ nova proposta

  /** "Quero essas férias": abre o formulário já preenchido com o caso mais comum. */
  protected escolher(ferias: FeriasDoColega): void {
    const primeiraMinha = this.minhasFerias()[0] ?? null;
    this.alvo.set(ferias);
    this.meuInicio.set(ferias.inicio); // eu fico com as datas dele...
    this.oferecer(primeiraMinha?.id ?? null); // ...e ele fica com as minhas
    this.meusDias.set(ferias.diasGozo);
    this.mensagem.set('');
    this.erroDaProposta.set(null);
  }

  protected oferecer(pedidoId: number | null): void {
    this.oferecida.set(pedidoId);
    const pedido = this.minhasFerias().find((p) => p.id === pedidoId);
    this.inicioDoColega.set(pedido?.inicio ?? '');
  }

  protected fecharProposta(): void {
    this.alvo.set(null);
  }

  protected propor(): void {
    const alvo = this.alvo();
    if (!alvo || !this.podePropor()) {
      return;
    }
    const oferecida = this.oferecida();
    const proposta: PropostaDeTroca = {
      pedidoDoColegaId: alvo.pedidoId,
      pedidoDoSolicitanteId: oferecida ?? undefined,
      inicioSolicitante: this.meuInicio(),
      diasSolicitante: oferecida ? undefined : this.meusDias(),
      inicioColega: this.inicioDoColega(),
      mensagem: this.mensagem().trim() || undefined,
    };
    this.enviando.set(true);
    this.erroDaProposta.set(null);
    this.api.proporTroca(proposta).subscribe({
      next: () => {
        this.enviando.set(false);
        this.alvo.set(null);
        this.carregar();
      },
      error: (resposta: HttpErrorResponse) => {
        this.enviando.set(false);
        this.erroDaProposta.set(erroDaApi(resposta));
      },
    });
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }
}

/** O erro da API pronto para a tela: a mensagem e, num 422 de regras, o checklist. */
function erroDaApi(resposta: HttpErrorResponse): ErroDaAcao {
  const problema = resposta.error as Problema | null;
  return {
    mensagem: problema?.detail ?? 'Não foi possível concluir. Tente de novo.',
    verificacoes: (problema?.verificacoes ?? []).filter((v) => v.resultado !== 'OK'),
  };
}

function amanhaIso(): string {
  const amanha = new Date();
  amanha.setDate(amanha.getDate() + 1);
  return amanha.toLocaleDateString('sv-SE');
}
