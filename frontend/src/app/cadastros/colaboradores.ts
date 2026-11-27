import { Component, computed, DestroyRef, inject, signal } from '@angular/core';
import { ActivatedRoute } from '@angular/router';
import { forkJoin } from 'rxjs';

import { Avatar } from '../compartilhado/avatar';
import { Icone } from '../compartilhado/icone';
import { diaMesAno } from '../nucleo/datas';
import { Colaborador, DepartamentoResumo, FiltrosDeColaboradores } from '../nucleo/modelos';
import { CadastrosApi, POR_PAGINA } from './cadastros-api';
import { faixaDaPagina, nomeDoPerfil, resumoDaEquipe, seloDoPerfil } from './colaboradores.regras';
import { PainelColaborador } from './painel-colaborador';

type Situacao = 'ativos' | 'inativos' | 'todos';

const ATIVO_DA_SITUACAO: Record<Situacao, boolean | null> = { ativos: true, inativos: false, todos: null };

/**
 * Cadastros > Colaboradores (só o RH).
 *
 * Uma tabela paginada com busca e filtros (que viram os parâmetros do GET /colaboradores) e um
 * painel lateral para cadastrar e editar. No celular, a tabela vira uma lista de cartões (só CSS).
 */
@Component({
  selector: 'crona-colaboradores',
  imports: [Avatar, Icone, PainelColaborador],
  templateUrl: './colaboradores.html',
  styleUrl: './colaboradores.css',
})
export class Colaboradores {
  private readonly api = inject(CadastrosApi);

  protected readonly estado = signal<'carregando' | 'pronto' | 'erro'>('carregando');

  // ------------------------------------------------------------------ filtros
  protected readonly busca = signal('');
  protected readonly departamentoId = signal<number | null>(null);
  protected readonly situacao = signal<Situacao>('ativos');
  protected readonly pagina = signal(0);
  protected readonly temFiltro = computed(
    () => this.busca().trim() !== '' || this.departamentoId() !== null || this.situacao() !== 'ativos',
  );

  // ------------------------------------------------------------------ o que veio da API
  protected readonly colaboradores = signal<Colaborador[]>([]);
  protected readonly total = signal(0);
  protected readonly totalPaginas = signal(0);
  /** Os números dos botões de situação (com a busca e o departamento atuais). */
  protected readonly contagem = signal({ ativos: 0, inativos: 0 });
  /** O subtítulo: a empresa inteira, sem filtro. */
  protected readonly resumo = signal<string | null>(null);
  protected readonly departamentos = signal<DepartamentoResumo[]>([]);

  // ------------------------------------------------------------------ painel
  /** null = fechado; 'novo' = cadastrar; um colaborador = editar. */
  protected readonly painel = signal<'novo' | Colaborador | null>(null);
  protected readonly idAberto = computed(() => {
    const p = this.painel();
    return p !== null && p !== 'novo' ? p.id : null;
  });
  /** O botão que abriu o painel: o foco volta para ele ao fechar. */
  private abriuPainel: HTMLElement | null = null;
  protected readonly feito = signal<string | null>(null);

  protected readonly situacoes: { valor: Situacao; texto: string }[] = [
    { valor: 'ativos', texto: 'Ativos' },
    { valor: 'inativos', texto: 'Inativos' },
    { valor: 'todos', texto: 'Todos' },
  ];

  protected readonly faixa = computed(() =>
    faixaDaPagina(this.pagina(), POR_PAGINA, this.colaboradores().length, this.total()),
  );

  protected readonly nomeDoPerfil = nomeDoPerfil;
  protected readonly seloDoPerfil = seloDoPerfil;
  protected readonly diaMesAno = diaMesAno;

  /** Espera a pessoa parar de digitar (300 ms) antes de buscar. */
  private esperaDaBusca: ReturnType<typeof setTimeout> | undefined;

  constructor() {
    // "Ver as pessoas" (em Departamentos) abre esta tela já filtrada: ?departamentoId=1
    const doLink = inject(ActivatedRoute).snapshot.queryParamMap.get('departamentoId');
    if (doLink && !Number.isNaN(Number(doLink))) {
      this.departamentoId.set(Number(doLink));
    }
    inject(DestroyRef).onDestroy(() => clearTimeout(this.esperaDaBusca));

    this.api.departamentos().subscribe({ next: (lista) => this.departamentos.set(lista), error: () => undefined });
    this.carregarResumo();
    this.carregar();
  }

  // ------------------------------------------------------------------ carregar

  /**
   * BUG-07: o número do último pedido. Quem troca de filtro rápido (Inativos e logo Todos) dispara
   * dois pedidos; se o primeiro responder por último, ele não pode sobrescrever a lista do segundo.
   * Mesma ideia do BUG-03 (solicitar.ts).
   */
  private ultimoPedido = 0;

  protected carregar(recarga = false): void {
    const numero = ++this.ultimoPedido;
    if (!recarga) {
      this.estado.set('carregando');
    }
    const filtros: FiltrosDeColaboradores = {
      busca: this.busca(),
      departamentoId: this.departamentoId(),
      ativo: ATIVO_DA_SITUACAO[this.situacao()],
      pagina: this.pagina(),
    };
    const base = { busca: filtros.busca, departamentoId: filtros.departamentoId };
    forkJoin({
      pagina: this.api.colaboradores(filtros),
      ativos: this.api.contar({ ...base, ativo: true }),
      inativos: this.api.contar({ ...base, ativo: false }),
    }).subscribe({
      next: ({ pagina, ativos, inativos }) => {
        if (numero !== this.ultimoPedido) {
          return; // resposta de um filtro que a pessoa já trocou
        }
        this.colaboradores.set(pagina.itens);
        this.total.set(pagina.total);
        this.totalPaginas.set(pagina.totalPaginas);
        this.contagem.set({ ativos, inativos });
        this.estado.set('pronto');
      },
      error: () => {
        if (numero === this.ultimoPedido) {
          this.estado.set('erro');
        }
      },
    });
  }

  private carregarResumo(): void {
    forkJoin({ total: this.api.contar({}), ativos: this.api.contar({ ativo: true }) }).subscribe({
      next: ({ total, ativos }) => this.resumo.set(resumoDaEquipe(total, ativos)),
      error: () => this.resumo.set(null),
    });
  }

  protected numeroDa(situacao: Situacao): number {
    const { ativos, inativos } = this.contagem();
    return situacao === 'ativos' ? ativos : situacao === 'inativos' ? inativos : ativos + inativos;
  }

  // ------------------------------------------------------------------ filtros

  protected digitouBusca(texto: string): void {
    this.busca.set(texto);
    clearTimeout(this.esperaDaBusca);
    this.esperaDaBusca = setTimeout(() => this.irParaPagina(0), 300);
  }

  protected escolheuDepartamento(valor: string): void {
    this.departamentoId.set(valor === '' ? null : Number(valor));
    this.irParaPagina(0);
  }

  protected escolheuSituacao(situacao: Situacao): void {
    this.situacao.set(situacao);
    this.irParaPagina(0);
  }

  protected limparFiltros(): void {
    clearTimeout(this.esperaDaBusca);
    this.busca.set('');
    this.departamentoId.set(null);
    this.situacao.set('ativos');
    this.irParaPagina(0);
  }

  protected irParaPagina(pagina: number): void {
    this.pagina.set(pagina);
    this.carregar(true);
  }

  // ------------------------------------------------------------------ painel

  protected abrirNovo(evento: Event): void {
    this.abriuPainel = evento.currentTarget as HTMLElement;
    this.feito.set(null);
    this.painel.set('novo');
  }

  protected abrirEdicao(colaborador: Colaborador, evento: Event): void {
    this.abriuPainel = evento.currentTarget as HTMLElement;
    this.feito.set(null);
    this.painel.set(colaborador);
  }

  protected fecharPainel(): void {
    this.painel.set(null);
    // O foco volta para quem abriu (o nome na tabela ou o "Novo colaborador").
    const voltar = this.abriuPainel;
    setTimeout(() => voltar?.focus());
  }

  /** O painel salvou: fecha, mostra o que aconteceu e recarrega a lista e os números. */
  protected salvou(mensagem: string): void {
    this.painel.set(null);
    this.feito.set(mensagem);
    this.carregar(true);
    this.carregarResumo();
    setTimeout(() => document.getElementById('feito')?.focus());
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLInputElement | HTMLSelectElement).value;
  }
}
