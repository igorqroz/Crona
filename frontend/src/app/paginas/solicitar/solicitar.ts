import { HttpErrorResponse } from '@angular/common/http';
import { Component, computed, inject, signal } from '@angular/core';
import { Router, RouterLink } from '@angular/router';

import { Checklist } from '../../compartilhado/checklist';
import { Icone } from '../../compartilhado/icone';
import { FeriasApi } from '../../ferias/ferias-api';
import { abonoMaximo, periodoEmFoco } from '../../ferias/periodos';
import { diaMes } from '../../nucleo/datas';
import { CalendarioDaEquipe, PedidoDeFerias, PeriodoAquisitivo, Problema, Simulacao, Verificacao } from '../../nucleo/modelos';
import { Calendario } from './calendario';
import { diasEntre, iso, montarMes, proximaSelecao, Selecao } from './calendario.regras';

/** CLT: um período de férias tem no máximo 30 dias de descanso. */
const MAXIMO_DE_DIAS = 30;

/**
 * Tela "Solicitar férias" (tela 4 do redesign).
 *
 * O fluxo:
 * 1. o calendário mostra 2 meses, com as férias dos colegas do departamento (GET /calendario);
 * 2. a pessoa clica no primeiro e no último dia;
 * 3. a tela pede a SIMULAÇÃO à API (as mesmas regras do envio) e mostra o resumo e o checklist;
 * 4. sem impedimento, o botão envia o pedido e volta para o Início.
 *
 * As regras de férias NÃO são reescritas aqui: quem decide é a API. A tela só mostra.
 */
@Component({
  selector: 'crona-solicitar',
  imports: [RouterLink, Icone, Calendario, Checklist],
  templateUrl: './solicitar.html',
  styleUrl: './solicitar.css',
})
export class Solicitar {
  private readonly api = inject(FeriasApi);
  private readonly router = inject(Router);

  private readonly hoje = iso(new Date());

  /** O 1º dia do primeiro mês na tela (o segundo mês é o seguinte). */
  protected readonly mesInicial = signal(primeiroDoMes(new Date()));
  protected readonly calendario = signal<CalendarioDaEquipe | null>(null);
  protected readonly periodo = signal<PeriodoAquisitivo | null>(null);

  protected readonly selecao = signal<Selecao>({ inicio: null, fim: null });
  protected readonly venderAbono = signal(false);
  protected readonly recado = signal('');

  protected readonly simulacao = signal<Simulacao | null>(null);
  protected readonly simulando = signal(false);
  protected readonly enviando = signal(false);
  protected readonly erro = signal<string | null>(null);
  /** O checklist que veio junto com um 422 no envio (o cenário mudou entre a simulação e o envio). */
  protected readonly verificacoesDoErro = signal<Verificacao[]>([]);

  /** Os 2 meses na tela, já com quem está fora e os feriados. Recalcula quando qualquer signal muda. */
  protected readonly meses = computed(() => {
    const dados = this.calendario();
    const contexto = { hoje: this.hoje, feriados: dados?.feriados ?? [], ausencias: dados?.ausencias ?? [] };
    const primeiro = this.mesInicial();
    const segundo = new Date(primeiro.getFullYear(), primeiro.getMonth() + 1, 1);
    return [
      montarMes(primeiro.getFullYear(), primeiro.getMonth(), contexto),
      montarMes(segundo.getFullYear(), segundo.getMonth(), contexto),
    ];
  });

  protected readonly dias = computed(() => {
    const { inicio, fim } = this.selecao();
    return inicio && fim ? diasEntre(inicio, fim) : 0;
  });

  protected readonly diasDeAbono = computed(() => (this.venderAbono() ? abonoMaximo(this.periodo()) : 0));

  protected readonly saldoDepois = computed(() => {
    const simulacao = this.simulacao();
    return simulacao ? simulacao.livresNoPeriodo - this.dias() - this.diasDeAbono() : null;
  });

  protected readonly verificacoes = computed(() =>
    this.verificacoesDoErro().length > 0 ? this.verificacoesDoErro() : (this.simulacao()?.verificacoes ?? []),
  );

  protected readonly podeEnviar = computed(() => !!this.simulacao()?.podeEnviar && !this.simulando() && !this.enviando());

  protected readonly paraQuem = computed(() => this.calendario()?.gestor?.nome ?? 'o gestor');

  protected readonly abonoMaximo = computed(() => abonoMaximo(this.periodo()));

  /** Para descartar respostas atrasadas: só vale a simulação do último clique. */
  private ultimaSimulacao = 0;

  constructor() {
    this.carregarCalendario();
    this.api.periodos().subscribe((periodos) => this.periodo.set(periodoEmFoco(periodos)));
  }

  // ------------------------------------------------------------------ calendário

  protected navegar(passo: -1 | 1): void {
    const atual = this.mesInicial();
    this.mesInicial.set(new Date(atual.getFullYear(), atual.getMonth() + passo, 1));
    this.carregarCalendario();
  }

  private carregarCalendario(): void {
    const primeiro = this.mesInicial();
    const ultimo = new Date(primeiro.getFullYear(), primeiro.getMonth() + 2, 0); // último dia do 2º mês
    this.api.calendario(iso(primeiro), iso(ultimo)).subscribe({
      next: (dados) => this.calendario.set(dados),
      error: () => this.erro.set('Não foi possível carregar o calendário da equipe.'),
    });
  }

  protected escolher(dia: string): void {
    this.selecao.set(proximaSelecao(this.selecao(), dia));
    this.simular();
  }

  protected alternarAbono(marcado: boolean): void {
    this.venderAbono.set(marcado);
    this.simular();
  }

  // ------------------------------------------------------------------ simulação e envio

  private pedido(): PedidoDeFerias | null {
    const { inicio, fim } = this.selecao();
    if (!inicio || !fim) {
      return null;
    }
    return { inicio, diasGozo: diasEntre(inicio, fim), diasAbono: this.diasDeAbono() };
  }

  private simular(): void {
    this.simulacao.set(null);
    this.erro.set(null);
    this.verificacoesDoErro.set([]);
    const pedido = this.pedido();
    if (!pedido) {
      return;
    }
    if (pedido.diasGozo > MAXIMO_DE_DIAS) {
      this.erro.set(`Um período de férias tem no máximo ${MAXIMO_DE_DIAS} dias. Você escolheu ${pedido.diasGozo}.`);
      return;
    }

    const numero = ++this.ultimaSimulacao;
    this.simulando.set(true);
    this.api.simular(pedido).subscribe({
      next: (simulacao) => {
        if (numero !== this.ultimaSimulacao) return; // chegou atrasada: a pessoa já clicou em outro dia
        this.simulando.set(false);
        this.simulacao.set(simulacao);
      },
      error: (resposta: HttpErrorResponse) => {
        if (numero !== this.ultimaSimulacao) return;
        this.simulando.set(false);
        this.erro.set((resposta.error as Problema | null)?.detail ?? 'Não foi possível conferir as datas.');
      },
    });
  }

  protected enviar(): void {
    const pedido = this.pedido();
    if (!pedido || !this.podeEnviar()) {
      return;
    }
    const recado = this.recado().trim();
    this.enviando.set(true);
    this.erro.set(null);
    this.api.enviar({ ...pedido, recado: recado || undefined }).subscribe({
      next: () => this.router.navigate(['/inicio']),
      error: (resposta: HttpErrorResponse) => {
        this.enviando.set(false);
        const problema = resposta.error as Problema | null;
        this.erro.set(problema?.detail ?? 'Não foi possível enviar o pedido.');
        this.verificacoesDoErro.set(problema?.verificacoes ?? []);
      },
    });
  }

  // ------------------------------------------------------------------ texto do resumo

  /** "17/03 · segunda" */
  protected comDiaDaSemana(dataIso: string): string {
    const semana = new Intl.DateTimeFormat('pt-BR', { weekday: 'long' })
      .format(new Date(`${dataIso}T12:00:00`))
      .replace('-feira', '');
    return `${diaMes(dataIso)} · ${semana}`;
  }
}

function primeiroDoMes(data: Date): Date {
  return new Date(data.getFullYear(), data.getMonth(), 1);
}
