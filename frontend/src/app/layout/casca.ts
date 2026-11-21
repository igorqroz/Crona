import { Component, computed, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

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

  protected readonly usuario = this.sessao.usuario;
  protected readonly perfil = computed(() => {
    const usuario = this.usuario();
    return usuario ? NOME_DO_PERFIL[usuario.perfil] : '';
  });

  protected readonly menu: { rota: string; texto: string; icone: NomeDoIcone }[] = [
    { rota: '/inicio', texto: 'Início', icone: 'inicio' },
    { rota: '/solicitar', texto: 'Solicitar férias', icone: 'sol' },
    { rota: '/trocas', texto: 'Trocas de período', icone: 'troca' },
    { rota: '/historico', texto: 'Histórico e avisos', icone: 'historico' },
  ];

  protected sair(): void {
    this.sessao.sair();
    this.router.navigate(['/login']);
  }
}
