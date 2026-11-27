import { Component, computed, effect, input, output, signal, untracked } from '@angular/core';

import { Checklist } from '../compartilhado/checklist';
import { Icone } from '../compartilhado/icone';
import { classeDoSelo } from '../compartilhado/selo';
import { intervalo } from '../nucleo/datas';
import { Decisao, TrocaParaAprovar } from '../nucleo/modelos';
import { acaoPrincipal, selo } from './aprovacoes.regras';
import { ErroDaDecisao } from './detalhe-pedido';

/**
 * Uma troca combinada entre dois colegas, esperando a decisão do gestor.
 * Mostra as datas de hoje e as novas dos DOIS, e os alertas de cada lado.
 */
@Component({
  selector: 'crona-troca-para-aprovar',
  imports: [Checklist, Icone],
  templateUrl: './troca-para-aprovar.html',
  styleUrl: './troca-para-aprovar.css',
})
export class TrocaParaAprovarCartao {
  readonly troca = input.required<TrocaParaAprovar>();
  readonly ocupado = input(false);
  readonly erro = input<ErroDaDecisao | null>(null);

  readonly aprovou = output<Decisao>();
  readonly recusou = output<string>();

  protected readonly mensagem = signal('');
  protected readonly aviso = signal<string | null>(null);

  protected readonly acao = computed(() => acaoPrincipal(this.troca().situacao));
  protected readonly classeDoSelo = classeDoSelo;
  protected readonly selo = computed(() => {
    const t = this.troca();
    return selo(t.situacao, [...t.verificacoesSolicitante, ...t.verificacoesColega].filter((v) => v.resultado !== 'OK'));
  });

  /** Os dois lados da troca, já com as datas de hoje e as novas (da proposta aceita). */
  protected readonly lados = computed(() => {
    const t = this.troca();
    const n = t.negociacao;
    const vigente = n.propostas[n.propostas.length - 1];
    return [
      {
        nome: n.solicitante.nome,
        hoje: n.pedidoDoSolicitante ? intervalo(n.pedidoDoSolicitante.inicio, n.pedidoDoSolicitante.fim) : 'sem férias marcadas',
        nova: intervalo(vigente.inicioSolicitante, vigente.fimSolicitante),
        alertas: t.verificacoesSolicitante.filter((v) => v.resultado !== 'OK'),
      },
      {
        nome: n.colega.nome,
        hoje: intervalo(n.pedidoDoColega.inicio, n.pedidoDoColega.fim),
        nova: intervalo(vigente.inicioColega, vigente.fimColega),
        alertas: t.verificacoesColega.filter((v) => v.resultado !== 'OK'),
      },
    ];
  });

  protected readonly recados = computed(() =>
    this.troca().negociacao.propostas.filter((p) => p.mensagem).map((p) => ({ autor: p.autor.nome, texto: p.mensagem })),
  );

  /**
   * BUG-04: só o id da negociação. Um computed só avisa quem depende dele quando o VALOR muda:
   * a mesma troca recarregada (outra ação na tela recarrega a fila) não apaga a mensagem que o
   * gestor está escrevendo. É a mesma ideia do detalhe-pedido.ts (BUG-02).
   */
  private readonly idDaTroca = computed(() => this.troca().negociacao.id);

  constructor() {
    // Outra troca neste cartão: começa com a mensagem em branco.
    effect(() => {
      this.idDaTroca();
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
      this.aviso.set('Para recusar, escreva o motivo: os dois colegas vão ver a mensagem.');
      return;
    }
    this.aviso.set(null);
    this.recusou.emit(mensagem);
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLTextAreaElement).value;
  }
}
