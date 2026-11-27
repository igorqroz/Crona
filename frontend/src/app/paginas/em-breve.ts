import { Component, input } from '@angular/core';

/**
 * Lugar das telas que ainda vão ser feitas nas próximas etapas da Fase 7.
 * O título vem do "data" da rota (withComponentInputBinding liga data → input).
 */
@Component({
  selector: 'crona-em-breve',
  template: `
    <header class="topo-pagina">
      <div class="topo-pagina-texto">
        <h1>{{ titulo() }}</h1>
      </div>
    </header>
    <div class="vazio">
      <strong>Em breve</strong>
      <p>Esta tela ainda está sendo feita.</p>
    </div>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 24px; max-width: 1180px; margin: 0 auto; }
  `,
})
export class EmBreve {
  readonly titulo = input('');
}
