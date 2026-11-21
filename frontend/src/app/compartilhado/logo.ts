import { Component } from '@angular/core';

/** O relógio com ondas + "CRONA", como no redesign. */
@Component({
  selector: 'crona-logo',
  template: `
    <svg width="32" height="32" viewBox="0 0 32 32" fill="none" aria-hidden="true">
      <rect x="12.5" y="1.5" width="7" height="3.2" rx="1.2" fill="#1B6FD4" />
      <path d="M16 4.5v2.2" stroke="#1B6FD4" stroke-width="2.4" />
      <circle cx="16" cy="18" r="11" stroke="#1B6FD4" stroke-width="2.6" />
      <path d="M16 11v6.2h4.2" stroke="#0D1B3E" stroke-width="2.4" stroke-linecap="round" stroke-linejoin="round" />
      <path d="M7.2 22.2c2.2-1.5 4.4-1.5 6.6 0s4.4 1.5 6.6 0c1.4-.9 2.8-1.2 4.2-.7" stroke="#1B6FD4" stroke-width="2" stroke-linecap="round" />
      <path d="M9.6 25.8c2-1.2 4-1.2 6 0s4 1.2 6 0" stroke="#1B6FD4" stroke-width="2" stroke-linecap="round" stroke-opacity="0.55" />
    </svg>
    <span>CRONA</span>
  `,
  styles: `
    :host { display: inline-flex; align-items: center; gap: 10px; }
    span {
      font-family: var(--fonte-titulo);
      font-size: 21px;
      font-weight: 800;
      letter-spacing: 0.6px;
      color: var(--cor-texto);
    }
  `,
})
export class Logo {}
