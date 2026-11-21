import { Component, input } from '@angular/core';

/**
 * Lugar das telas que ainda vão ser feitas nas próximas etapas da Fase 7.
 * O título vem do "data" da rota (withComponentInputBinding liga data → input).
 */
@Component({
  selector: 'crona-em-breve',
  template: `
    <h1>{{ titulo() }}</h1>
    <section class="cartao">
      <p class="texto-suave">Esta tela chega nas próximas etapas da Fase 7.</p>
    </section>
  `,
  styles: `
    :host { display: flex; flex-direction: column; gap: 24px; max-width: 1180px; margin: 0 auto; }
    h1 { margin: 0; font-family: var(--fonte-titulo); font-size: 32px; font-weight: 800; letter-spacing: -0.8px; }
    p { margin: 0; }
  `,
})
export class EmBreve {
  readonly titulo = input('');
}
