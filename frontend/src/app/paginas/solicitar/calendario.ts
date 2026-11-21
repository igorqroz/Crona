import { Component, input, output } from '@angular/core';

import { Icone } from '../../compartilhado/icone';
import { barra, Dia, Mes } from './calendario.regras';

const DIAS_DA_SEMANA = ['D', 'S', 'T', 'Q', 'Q', 'S', 'S'];

/**
 * O calendário de 2 meses do redesign. É um componente de APRESENTAÇÃO: recebe tudo pronto
 * (os meses, o que está selecionado, o limite) por input() e só avisa a página, por output(),
 * quando a pessoa clica num dia ou nas setas. Quem decide o que fazer com o clique é a página.
 */
@Component({
  selector: 'crona-calendario',
  imports: [Icone],
  templateUrl: './calendario.html',
  styleUrl: './calendario.css',
})
export class Calendario {
  readonly meses = input.required<Mes[]>();
  readonly inicio = input<string | null>(null);
  readonly fim = input<string | null>(null);
  readonly limite = input<number | null>(null);

  readonly escolheu = output<string>();
  readonly navegou = output<-1 | 1>();

  protected readonly diasDaSemana = DIAS_DA_SEMANA;

  /** Os quadrados vazios antes do dia 1 (para o dia 1 cair na coluna certa da semana). */
  protected vazios(mes: Mes): null[] {
    return Array<null>(mes.vazios).fill(null);
  }

  protected barra(dia: Dia) {
    return barra(dia.fora.length, this.limite());
  }

  protected ehPonta(dia: Dia): boolean {
    return dia.iso === this.inicio() || dia.iso === this.fim();
  }

  protected noIntervalo(dia: Dia): boolean {
    const inicio = this.inicio();
    const fim = this.fim();
    return !!inicio && !!fim && dia.iso > inicio && dia.iso < fim;
  }

  /** O que um leitor de tela fala: "17 de março, segunda, 3 colegas fora". */
  protected descricao(dia: Dia, mes: Mes): string {
    const nomeDoMes = mes.titulo.split(' ')[0].toLowerCase();
    const semana = new Intl.DateTimeFormat('pt-BR', { weekday: 'long' })
      .format(new Date(`${dia.iso}T12:00:00`))
      .replace('-feira', '');
    const fora =
      dia.fora.length === 0 ? 'ninguém da equipe fora' : dia.fora.length === 1 ? '1 colega fora' : `${dia.fora.length} colegas fora`;
    return `${dia.numero} de ${nomeDoMes}, ${semana}${dia.feriado ? ', feriado' : ''}, ${fora}`;
  }
}
