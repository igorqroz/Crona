import { Component, computed, input } from '@angular/core';

/**
 * Os ícones do redesign, num lugar só: <crona-icone nome="inicio" />.
 * Antes, cada tela copiava o SVG inteiro; agora, trocar um ícone muda o sistema todo.
 * Cada ícone é uma lista de traços ("d" de <path>), desenhados com a cor do texto (currentColor).
 */
const TRACOS = {
  inicio: ['M4 10.5 12 4l8 6.5V20a1 1 0 0 1-1 1h-4.5v-6h-5v6H5a1 1 0 0 1-1-1v-9.5Z'],
  sol: [
    'M16 12a4 4 0 1 1-8 0 4 4 0 0 1 8 0Z',
    'M12 2.5v2M12 19.5v2M4.6 4.6 6 6M18 18l1.4 1.4M2.5 12h2M19.5 12h2M4.6 19.4 6 18M18 6l1.4-1.4',
  ],
  troca: ['M7 4 3 8l4 4', 'M3 8h13', 'm17 20 4-4-4-4', 'M21 16H8'],
  historico: ['M3.5 12a8.5 8.5 0 1 0 2.6-6.1', 'M3.5 4.5v4h4', 'M12 7.5V12l3 2'],
  sair: ['M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4', 'm16 17 5-5-5-5', 'M21 12H9'],
  check: ['m5 12.5 4.5 4.5L19 7.5'],
  alerta: ['M12 3.5 2.8 19.5h18.4L12 3.5Z', 'M12 10v4', 'M12 17h.01'],
} as const;

export type NomeDoIcone = keyof typeof TRACOS;

@Component({
  selector: 'crona-icone',
  template: `
    <svg
      [attr.width]="tamanho()"
      [attr.height]="tamanho()"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      [attr.stroke-width]="espessura()"
      stroke-linecap="round"
      stroke-linejoin="round"
      aria-hidden="true"
    >
      @for (d of tracos(); track $index) {
        <path [attr.d]="d" />
      }
    </svg>
  `,
  styles: `:host { display: inline-flex; flex-shrink: 0; }`,
})
export class Icone {
  readonly nome = input.required<NomeDoIcone>();
  readonly tamanho = input(19);
  readonly espessura = input(1.75);

  protected readonly tracos = computed(() => TRACOS[this.nome()]);
}
