import { Component, computed, inject, signal } from '@angular/core';

import { Avatar } from '../compartilhado/avatar';
import { Icone } from '../compartilhado/icone';
import { CalendarioDaGestao } from '../nucleo/modelos';
import { iso } from '../paginas/solicitar/calendario.regras';
import { AprovacoesApi } from './aprovacoes-api';
import { Janela, montarGrade, segundaDaSemana, somarDias } from './calendario-equipe.regras';

/**
 * Tela "Calendário da equipe" (Fase 8c): o "Quem está fora" do Painel do redesign.
 * O gestor vê a equipe dele; o RH (ADMIN), a empresa toda. Quem decide isso é a API.
 */
@Component({
  selector: 'crona-calendario-equipe',
  imports: [Avatar, Icone],
  templateUrl: './calendario-equipe.html',
  styleUrl: './calendario-equipe.css',
})
export class CalendarioEquipe {
  private readonly api = inject(AprovacoesApi);
  private readonly hoje = iso(new Date());

  protected readonly janela = signal<Janela>(6);
  /** A segunda-feira em que a janela começa. */
  protected readonly inicio = signal(segundaDaSemana(new Date()));
  protected readonly dados = signal<CalendarioDaGestao | null>(null);
  protected readonly estado = signal<'carregando' | 'pronto' | 'erro'>('carregando');

  protected readonly grade = computed(() => {
    const dados = this.dados();
    return dados ? montarGrade(dados, this.hoje) : null;
  });

  /** As colunas: a dos nomes (fixa) e uma por dia, que dividem o espaço que sobra. */
  protected readonly colunas = computed(() => `200px repeat(${this.grade()?.dias.length ?? 1}, minmax(8px, 1fr))`);

  protected readonly janelas: { valor: Janela; texto: string }[] = [
    { valor: 2, texto: '2 semanas' },
    { valor: 6, texto: '6 semanas' },
    { valor: 13, texto: 'Trimestre' },
  ];

  constructor() {
    this.carregar();
  }

  protected mudarJanela(janela: Janela): void {
    this.janela.set(janela);
    this.carregar();
  }

  /** Anda uma janela inteira para trás ou para a frente. */
  protected navegar(sentido: -1 | 1): void {
    this.inicio.set(somarDias(this.inicio(), sentido * this.janela() * 7));
    this.carregar();
  }

  protected voltarParaHoje(): void {
    this.inicio.set(segundaDaSemana(new Date()));
    this.carregar();
  }

  protected carregar(): void {
    const de = this.inicio();
    const ate = somarDias(de, this.janela() * 7 - 1);
    this.estado.set('carregando');
    this.api.calendario(de, ate).subscribe({
      next: (dados) => {
        this.dados.set(dados);
        this.estado.set('pronto');
      },
      error: () => this.estado.set('erro'),
    });
  }

  /** O "grid-column" do CSS de uma faixa: a 1ª coluna é a dos nomes, por isso o +2. */
  protected faixa(de: number, ate: number): string {
    return `${de + 2} / ${ate + 3}`;
  }
}
