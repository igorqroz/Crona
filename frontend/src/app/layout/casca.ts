import { NgTemplateOutlet } from '@angular/common';
import { Component, computed, inject } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { filter, interval } from 'rxjs';

import { Pendencias } from '../ferias/pendencias';
import { Sino } from '../notificacoes/sino';

import { Avatar } from '../compartilhado/avatar';
import { Icone, NomeDoIcone } from '../compartilhado/icone';
import { Logo } from '../compartilhado/logo';
import { Perfil } from '../nucleo/modelos';
import { Sessao } from '../nucleo/sessao';

interface ItemDoMenu {
  rota: string;
  texto: string;
  icone: NomeDoIcone;
  contador?: () => number;
}

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
  imports: [RouterOutlet, RouterLink, RouterLinkActive, NgTemplateOutlet, Logo, Icone, Avatar, Sino],
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

  protected readonly menu: ItemDoMenu[] = [
    { rota: '/inicio', texto: 'Início', icone: 'inicio' },
    { rota: '/solicitar', texto: 'Solicitar férias', icone: 'sol' },
    { rota: '/trocas', texto: 'Trocas de período', icone: 'troca', contador: this.pendencias.trocas },
    { rota: '/historico', texto: 'Histórico e avisos', icone: 'historico' },
  ];

  /** A seção "Gestão" só existe para gestor e RH. O gestor também é colaborador: vê as duas. */
  protected readonly menuDeGestao = computed<ItemDoMenu[]>(() => {
    const perfil = this.usuario()?.perfil;
    return perfil === 'GESTOR' || perfil === 'ADMIN'
      ? [
          { rota: '/aprovacoes', texto: 'Aprovações', icone: 'aprovacoes', contador: this.pendencias.aprovacoes },
          { rota: '/calendario-equipe', texto: 'Calendário da equipe', icone: 'calendario' },
        ]
      : [];
  });

  /** A seção "Cadastros" é só do RH (ADMIN). */
  protected readonly menuDeCadastros = computed<ItemDoMenu[]>(() =>
    this.usuario()?.perfil === 'ADMIN'
      ? [{ rota: '/cadastros/departamentos', texto: 'Departamentos', icone: 'predio' }]
      : [],
  );

  constructor() {
    // A cada troca de tela, confere de novo o que está esperando a pessoa (os números do menu).
    // Não precisa chamar também aqui no construtor: a casca nasce DURANTE a primeira navegação,
    // então o primeiro NavigationEnd já chega a esta inscrição.
    this.router.events
      .pipe(
        filter((evento) => evento instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.pendencias.atualizar());

    // Fase 8b: e a cada minuto, para uma notificação nova aparecer mesmo sem trocar de tela.
    interval(60_000)
      .pipe(takeUntilDestroyed())
      .subscribe(() => this.pendencias.atualizar());
  }

  protected sair(): void {
    this.sessao.sair();
    this.router.navigate(['/login']);
  }
}
