import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';

/**
 * O componente raiz: só tem o lugar onde o roteador desenha a tela atual
 * (o login, ou a "casca" com o menu lateral e a página dentro dela).
 */
@Component({
  selector: 'crona-raiz',
  imports: [RouterOutlet],
  template: `<router-outlet />`,
})
export class App {}
