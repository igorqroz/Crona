import { Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter } from 'rxjs';

import { Pendencias } from '../ferias/pendencias';

import { Avatar } from '../compartilhado/avatar';
import { Icone, NomeDoIcone } from '../compartilhado/icone';
import { Logo } from '../compartilhado/logo';
import { Perfil } from '../nucleo/modelos';
import { Sessao } from '../nucleo/sessao';

const NOME_DO_PERFIL: Record<Perfil, string> = {
  COLABORADOR: 'Colaborador',
  GESTOR: 'Gestor',
  ADMIN: 'Administrador (RH)',
};

/**
 * A "casca" das telas de quem está logado: o menu lateral à esquerda e, à direita,
 * o <router-outlet>, onde o roteador desenha a página da vez (Início, Solicitar...).
 */
@Component({
  selector: 'crona-casca',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, Logo, Icone, Avatar],
  templateUrl: './casca.html',
  styleUrl: './casca.css',
})
export class Casca {
  private readonly sessao = inject(Sessao);
  private readonly router = inject(Router);
  private readonly pendencias = inject(Pendencias);

  protected readonly usuario = this.sessao.usuario;
  protected readonly perfil = computed(() => {
    const usuario = this.usuario();
    return usuario ? NOME_DO_PERFIL[usuario.perfil] : '';
  });

  protected readonly menu: { rota: string; texto: string; icone: NomeDoIcone; contador?: () => number }[] = [
    { rota: '/inicio', texto: 'Início', icone: 'inicio' },
    { rota: '/solicitar', texto: 'Solicitar férias', icone: 'sol' },
    { rota: '/trocas', texto: 'Trocas de período', icone: 'troca', contador: this.pendencias.trocas },
    { rota: '/historico', texto: 'Histórico e avisos', icone: 'historico' },
  ];

  constructor() {
    // A cada troca de tela, confere de novo se há troca esperando resposta (o número do menu).
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.pendencias.atualizar());
    this.pendencias.atualizar();
  }

  protected sair(): void {
    this.sessao.sair();
    this.router.navigate(['/login']);
  }
}
