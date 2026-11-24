import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { forkJoin, Observable, of } from 'rxjs';

import { Avatar } from '../compartilhado/avatar';
import { Icone } from '../compartilhado/icone';
import { Pendencias } from '../ferias/pendencias';
import { intervalo } from '../nucleo/datas';
import {
  Decisao,
  DetalheDaAprovacao,
  FeriasAprovada,
  ItemDeAprovacao,
  Problema,
  TrocaParaAprovar,
} from '../nucleo/modelos';
import { Sessao } from '../nucleo/sessao';
import { AprovacoesApi } from './aprovacoes-api';
import { contagens, Filtro, filtrar, selo } from './aprovacoes.regras';
import { DetalhePedido, ErroDaDecisao } from './detalhe-pedido';
import { TrocaParaAprovarCartao } from './troca-para-aprovar';

type Aba = 'pedidos' | 'trocas' | 'rh' | 'aprovadas';

/**
 * Tela "Aprovações" do gestor (tela 2 do redesign), em 3 abas:
 * - Pedidos: lista à esquerda (com filtro) e o detalhe do escolhido à direita;
 * - Trocas: as negociações que os dois colegas já combinaram;
 * - Confirmação do RH: só para o ADMIN, quando a empresa liga a EMP-06;
 * - Aprovadas: só para o ADMIN, que pode cancelar férias aprovadas por engano (até a véspera).
 */
@Component({
  selector: 'crona-aprovacoes',
  imports: [Avatar, Icone, DetalhePedido, TrocaParaAprovarCartao],
  templateUrl: './aprovacoes.html',
  styleUrl: './aprovacoes.css',
})
export class Aprovacoes {
  private readonly api = inject(AprovacoesApi);
  private readonly pendencias = inject(Pendencias);
  protected readonly ehRh = inject(Sessao).usuario()?.perfil === 'ADMIN';

  protected readonly aba = signal<Aba>('pedidos');
  protected readonly estado = signal<'carregando' | 'pronto' | 'erro'>('carregando');
  /** Uma mensagem de sucesso no topo ("Pedido de Ana aprovado"). */
  protected readonly feito = signal<string | null>(null);

  // ------------------------------------------------------------------ pedidos
  protected readonly pedidos = signal<ItemDeAprovacao[]>([]);
  protected readonly filtro = signal<Filtro>('TODOS');
  protected readonly visiveis = computed(() => filtrar(this.pedidos(), this.filtro()));
  protected readonly contagens = computed(() => contagens(this.pedidos()));
  protected readonly selecionado = signal<number | null>(null);
  protected readonly detalhe = signal<DetalheDaAprovacao | null>(null);

  // ------------------------------------------------------------------ trocas e RH
  protected readonly trocas = signal<TrocaParaAprovar[]>([]);
  protected readonly filaDoRh = signal<ItemDeAprovacao[]>([]);
  protected readonly recusandoNoRh = signal<number | null>(null);
  protected readonly motivoDoRh = signal('');

  // ------------------------------------------------------------------ RH: férias aprovadas
  protected readonly aprovadas = signal<FeriasAprovada[]>([]);
  /** Qual das férias aprovadas está com o campo de motivo aberto. */
  protected readonly cancelando = signal<number | null>(null);
  protected readonly motivoDoCancelamento = signal('');

  // ------------------------------------------------------------------ ações
  /** Qual item tem uma ação indo para a API ("pedido-3", "troca-7", "rh-5", "aprovada-9"), e o erro dela. */
  protected readonly ocupado = signal<string | null>(null);
  protected readonly erro = signal<{ chave: string; erro: ErroDaDecisao } | null>(null);

  protected readonly filtros: { valor: Filtro; texto: string }[] = [
    { valor: 'TODOS', texto: 'Todos' },
    { valor: 'COM_CONFLITO', texto: 'Com conflito' },
    { valor: 'SEM_CONFLITO', texto: 'Sem conflito' },
  ];

  protected readonly selo = selo;
  protected readonly intervalo = intervalo;

  constructor() {
    this.carregar();
  }

  protected carregar(): void {
    forkJoin({
      pedidos: this.api.fila(),
      trocas: this.api.trocas(),
      rh: this.ehRh ? this.api.filaDoRh() : of([]),
      aprovadas: this.ehRh ? this.api.aprovadas() : of([]),
    }).subscribe({
      next: ({ pedidos, trocas, rh, aprovadas }) => {
        this.pedidos.set(pedidos);
        this.trocas.set(trocas);
        this.filaDoRh.set(rh);
        this.aprovadas.set(aprovadas);
        this.estado.set('pronto');
        this.pendencias.atualizar();
        // Mantém o pedido escolhido; se ele saiu da fila (foi decidido), escolhe o primeiro.
        const atual = this.selecionado();
        const proximo = this.visiveis().find((p) => p.id === atual) ?? this.visiveis()[0] ?? null;
        this.escolher(proximo?.id ?? null);
      },
      error: () => this.estado.set('erro'),
    });
  }

  protected mudarAba(aba: Aba): void {
    this.aba.set(aba);
    this.feito.set(null); // a mensagem de sucesso era da outra aba
    this.erro.set(null);
  }

  protected mudarFiltro(filtro: Filtro): void {
    this.filtro.set(filtro);
    if (!this.visiveis().some((p) => p.id === this.selecionado())) {
      this.escolher(this.visiveis()[0]?.id ?? null);
    }
  }

  protected escolher(id: number | null): void {
    this.selecionado.set(id);
    this.erro.set(null);
    if (id === null) {
      this.detalhe.set(null);
      return;
    }
    this.api.detalhe(id).subscribe({
      next: (detalhe) => this.detalhe.set(detalhe),
      error: () => this.detalhe.set(null),
    });
  }

  // ------------------------------------------------------------------ decisões

  protected aprovarPedido(id: number, decisao: Decisao): void {
    const nome = this.nomeNoPedido(id);
    this.executar(`pedido-${id}`, this.api.aprovar(id, decisao), `Pedido de ${nome} aprovado.`);
  }

  protected recusarPedido(id: number, mensagem: string): void {
    const nome = this.nomeNoPedido(id);
    this.executar(`pedido-${id}`, this.api.recusar(id, mensagem), `Pedido de ${nome} recusado.`);
  }

  protected aprovarSemConflito(): void {
    this.ocupado.set('lote');
    this.api.aprovarSemConflito().subscribe({
      next: (aprovados) => {
        this.ocupado.set(null);
        this.feito.set(aprovados.length === 1 ? '1 pedido aprovado.' : `${aprovados.length} pedidos aprovados.`);
        this.carregar();
      },
      error: () => {
        this.ocupado.set(null);
        this.feito.set(null);
      },
    });
  }

  protected aprovarTroca(id: number, decisao: Decisao): void {
    this.executar(`troca-${id}`, this.api.aprovarTroca(id, decisao), 'Troca aprovada: as férias dos dois foram atualizadas.');
  }

  protected recusarTroca(id: number, mensagem: string): void {
    this.executar(`troca-${id}`, this.api.recusarTroca(id, mensagem), 'Troca recusada.');
  }

  protected confirmarNoRh(id: number): void {
    this.executar(`rh-${id}`, this.api.confirmarNoRh(id), 'Pedido confirmado pelo RH.');
  }

  protected recusarNoRh(id: number): void {
    const motivo = this.motivoDoRh().trim();
    if (!motivo) {
      this.erro.set({ chave: `rh-${id}`, erro: { mensagem: 'Escreva o motivo da recusa.', verificacoes: [] } });
      return;
    }
    this.executar(`rh-${id}`, this.api.recusarNoRh(id, motivo), 'Pedido recusado pelo RH.');
  }

  /** O RH cancela férias aprovadas por engano. O motivo vai para o colaborador. */
  protected cancelarAprovada(ferias: FeriasAprovada): void {
    const motivo = this.motivoDoCancelamento().trim();
    const chave = `aprovada-${ferias.id}`;
    if (!motivo) {
      this.erro.set({ chave, erro: { mensagem: 'Escreva o motivo do cancelamento.', verificacoes: [] } });
      return;
    }
    const nome = ferias.colaborador.nome.split(' ')[0];
    this.executar(chave, this.api.cancelarAprovada(ferias.id, motivo),
      `Férias de ${nome} canceladas. Os dias voltaram para o saldo e ${nome} foi avisado(a).`);
  }

  /**
   * Faltam 3 dias ou menos para o início? O pagamento das férias sai até 2 dias antes (CLT-08),
   * então ele pode já ter sido feito: a tela avisa antes de cancelar.
   */
  protected pagamentoPodeTerSaido(inicio: string): boolean {
    const [ano, mes, dia] = inicio.split('-').map(Number);
    const hoje = new Date();
    const faltam = Math.round(
      (new Date(ano, mes - 1, dia).getTime() - new Date(hoje.getFullYear(), hoje.getMonth(), hoje.getDate()).getTime()) /
        86_400_000,
    );
    return faltam <= 3;
  }

  /** Toda decisão segue o mesmo roteiro: marca ocupado, chama a API, recarrega ou mostra o erro. */
  private executar(chave: string, acao: Observable<unknown>, sucesso: string): void {
    this.ocupado.set(chave);
    this.erro.set(null);
    this.feito.set(null);
    acao.subscribe({
      next: () => {
        this.ocupado.set(null);
        this.recusandoNoRh.set(null);
        this.motivoDoRh.set('');
        this.cancelando.set(null);
        this.motivoDoCancelamento.set('');
        this.feito.set(sucesso);
        this.carregar();
      },
      error: (resposta: HttpErrorResponse) => {
        this.ocupado.set(null);
        const problema = resposta.error as Problema | null;
        this.erro.set({
          chave,
          erro: {
            mensagem: problema?.detail ?? 'Não foi possível concluir. Tente de novo.',
            verificacoes: (problema?.verificacoes ?? []).filter((v) => v.resultado !== 'OK'),
          },
        });
      },
    });
  }

  protected erroDe(chave: string): ErroDaDecisao | null {
    const erro = this.erro();
    return erro?.chave === chave ? erro.erro : null;
  }

  private nomeNoPedido(id: number): string {
    return this.pedidos().find((p) => p.id === id)?.colaborador.nome.split(' ')[0] ?? 'colaborador';
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLTextAreaElement).value;
  }
}
