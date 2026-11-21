import { Component, input } from '@angular/core';

import { Verificacao } from '../nucleo/modelos';
import { Icone } from './icone';

/**
 * O checklist das regras de férias (CLT e da empresa), como vem da API.
 * Usado na tela Solicitar (simulação) e na tela Trocas (quando a API recusa as datas com 422).
 */
@Component({
  selector: 'crona-checklist',
  imports: [Icone],
  template: `
    @if (resumo()) {
      <strong class="resumo">{{ resumo() }}</strong>
    }
    <ul>
      @for (v of verificacoes(); track $index) {
        <li [class]="v.resultado">
          <span class="marca">
            @switch (v.resultado) {
              @case ('OK') {
                <crona-icone nome="check" [tamanho]="14" [espessura]="2.6" />
              }
              @case ('ATENCAO') {
                <crona-icone nome="alerta" [tamanho]="14" [espessura]="2.2" />
              }
              @case ('IMPEDIMENTO') {
                <crona-icone nome="x" [tamanho]="14" [espessura]="2.6" />
              }
            }
          </span>
          <span class="texto">{{ v.mensagem }}</span>
        </li>
      }
    </ul>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 10px; }
    .resumo { font-size: 13px; font-weight: 700; }
    ul { list-style: none; margin: 0; padding: 0; display: flex; flex-direction: column; gap: 8px; }
    li { display: flex; align-items: flex-start; gap: 10px; font-size: 13px; color: var(--cor-texto-secundario); }
    .marca {
      width: 24px; height: 24px; border-radius: 7px; flex-shrink: 0;
      display: flex; align-items: center; justify-content: center;
    }
    .texto { padding-top: 3px; }
    .OK .marca { background: var(--cor-sucesso-fundo); color: var(--cor-sucesso); }
    .ATENCAO .marca { background: var(--cor-alerta-fundo); color: var(--cor-alerta-texto); }
    .IMPEDIMENTO .marca { background: #FDECEC; color: #B42318; }
    .IMPEDIMENTO .texto { color: #B42318; font-weight: 600; }
  `,
})
export class Checklist {
  readonly verificacoes = input.required<Verificacao[]>();
  readonly resumo = input<string | null>(null);
}
