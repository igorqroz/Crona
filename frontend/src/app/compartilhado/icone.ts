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
  x: ['M18 6 6 18', 'm6 6 12 12'],
  documento: ['M14 3H7a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V8l-5-5Z', 'M14 3v5h5', 'M9 13h6M9 17h4'],
  baixar: ['M12 4v11m0 0-4.5-4.5M12 15l4.5-4.5', 'M4.5 19.5h15'],
  calendario: ['M8 2.5v3M16 2.5v3', 'M3.5 9.5h17', 'M5 4.5h14a1.5 1.5 0 0 1 1.5 1.5v13a1.5 1.5 0 0 1-1.5 1.5H5A1.5 1.5 0 0 1 3.5 19V6A1.5 1.5 0 0 1 5 4.5Z'],
  sino: ['M6 8a6 6 0 0 1 12 0c0 7 3 9 3 9H3s3-2 3-9', 'M10.3 21a1.94 1.94 0 0 0 3.4 0'],
  aprovacoes: ['M4 6.5h16v12a1.5 1.5 0 0 1-1.5 1.5h-13A1.5 1.5 0 0 1 4 18.5v-12Z', 'M4 6.5 6 3.5h12l2 3', 'm9 13 2 2 4-4'],
  predio: ['M4 21V5a1.5 1.5 0 0 1 1.5-1.5h8A1.5 1.5 0 0 1 15 5v16', 'M15 9.5h3.5A1.5 1.5 0 0 1 20 11v10', 'M2.5 21h19', 'M8 8h3M8 12h3M8 16h3'],
  anterior: ['m15 18-6-6 6-6'],
  proximo: ['m9 18 6-6-6-6'],
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
