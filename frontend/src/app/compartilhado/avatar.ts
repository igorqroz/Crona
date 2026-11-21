import { Component, computed, input } from '@angular/core';

/** Os pares fundo/texto dos avatares do redesign. */
const CORES = [
  ['#FBE6D3', '#8A3F06'],
  ['#E8E3FA', '#4B3A99'],
  ['#D9F1E6', '#05664A'],
  ['#E6EAF1', '#3D4A66'],
  ['#DCE8FB', '#14569F'],
] as const;

/** "Ana Lima" → "AL" */
export function iniciais(nome: string): string {
  const partes = nome.trim().split(/\s+/);
  const primeira = partes[0]?.[0] ?? '';
  const ultima = partes.length > 1 ? partes[partes.length - 1][0] : '';
  return (primeira + ultima).toUpperCase();
}

/** Bolinha com as iniciais. A cor sai do nome, então a mesma pessoa tem sempre a mesma cor. */
@Component({
  selector: 'crona-avatar',
  template: `{{ texto() }}`,
  host: {
    'aria-hidden': 'true',
    '[style.width.px]': 'tamanho()',
    '[style.height.px]': 'tamanho()',
    '[style.font-size.px]': 'tamanho() * 0.36',
    '[style.background]': 'cor()[0]',
    '[style.color]': 'cor()[1]',
  },
  styles: `
    :host {
      display: inline-flex;
      align-items: center;
      justify-content: center;
      flex-shrink: 0;
      border-radius: 50%;
      font-family: var(--fonte-titulo);
      font-weight: 700;
    }
  `,
})
export class Avatar {
  readonly nome = input.required<string>();
  readonly tamanho = input(32);

  protected readonly texto = computed(() => iniciais(this.nome()));
  protected readonly cor = computed(() => {
    const soma = [...this.nome()].reduce((total, letra) => total + letra.charCodeAt(0), 0);
    return CORES[soma % CORES.length];
  });
}
