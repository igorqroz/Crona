import { HttpErrorResponse } from '@angular/common/http';
import { afterNextRender, Component, computed, ElementRef, inject, input, OnInit, output, signal, viewChild } from '@angular/core';
import { Observable } from 'rxjs';

import { Avatar } from '../compartilhado/avatar';
import { Icone } from '../compartilhado/icone';
import { diaMesAno } from '../nucleo/datas';
import { Colaborador, DadosDoColaborador, DepartamentoResumo, Perfil } from '../nucleo/modelos';
import { Sessao } from '../nucleo/sessao';
import { CadastrosApi } from './cadastros-api';
import {
  avisoDeEquipe,
  descricaoDoPerfil,
  errosDaApi,
  ErrosDoFormulario,
  gestoresPossiveis,
  nomeDoPerfil,
  PERFIS,
  primeiroNome,
  seloDoPerfil,
  validar,
} from './colaboradores.regras';

/**
 * O painel lateral de Colaboradores: cadastra (colaborador = null) ou edita uma pessoa.
 *
 * É uma janela (role="dialog", aria-modal): ao abrir, o foco vai para o primeiro campo; o Tab fica
 * preso dentro dela; Esc, o X, o "Cancelar" e o clique no fundo escuro fecham. Quem abriu (a lista)
 * cuida de devolver o foco ao botão de origem.
 */
@Component({
  selector: 'crona-painel-colaborador',
  imports: [Avatar, Icone],
  templateUrl: './painel-colaborador.html',
  styleUrl: './painel-colaborador.css',
  host: { '(keydown)': 'tecla($event)' },
})
export class PainelColaborador implements OnInit {
  private readonly api = inject(CadastrosApi);

  readonly colaborador = input<Colaborador | null>(null);
  readonly departamentos = input<DepartamentoResumo[]>([]);

  readonly fechou = output<void>();
  /** Deu certo: a frase para a faixa verde da lista. */
  readonly salvou = output<string>();

  private readonly janela = viewChild.required<ElementRef<HTMLElement>>('janela');

  protected readonly novo = computed(() => this.colaborador() === null);

  /** BUG-06: é o painel de quem está logado? Aí não dá para desativar nem mudar o próprio perfil. */
  private readonly sessao = inject(Sessao);
  protected readonly souEu = computed(() => {
    const c = this.colaborador();
    return c !== null && c.id === this.sessao.usuario()?.id;
  });

  // ------------------------------------------------------------------ o formulário
  protected readonly nome = signal('');
  protected readonly email = signal('');
  protected readonly senha = signal('');
  protected readonly perfil = signal<Perfil>('COLABORADOR');
  protected readonly cargo = signal('');
  protected readonly dataAdmissao = signal('');
  protected readonly departamentoId = signal<number | null>(null);
  protected readonly gestorId = signal<number | null>(null);

  protected readonly erros = signal<ErrosDoFormulario>({});
  /** Uma recusa da API que não é de um campo (ex.: a regra do gestor). */
  protected readonly erroGeral = signal<string | null>(null);
  protected readonly ocupado = signal(false);

  // ------------------------------------------------------------------ gestores e acesso
  private readonly gestores = signal<Colaborador[]>([]);
  protected readonly opcoesDeGestor = computed(() =>
    gestoresPossiveis(this.gestores(), this.colaborador()?.id ?? null),
  );
  /** Quantas pessoas ATIVAS têm esta pessoa como gestor (null = ainda não sei). */
  protected readonly equipe = signal<number | null>(null);
  protected readonly confirmandoDesativar = signal(false);

  protected readonly perfis = PERFIS;
  protected readonly nomeDoPerfil = nomeDoPerfil;
  protected readonly seloDoPerfil = seloDoPerfil;
  protected readonly descricaoDoPerfil = descricaoDoPerfil;
  protected readonly primeiroNome = primeiroNome;
  protected readonly avisoDeEquipe = avisoDeEquipe;
  protected readonly diaMesAno = diaMesAno;

  constructor() {
    // Ao abrir, o foco vai para o primeiro campo (o nome).
    afterNextRender(() => document.getElementById('painel-nome')?.focus());
  }

  ngOnInit(): void {
    const c = this.colaborador();
    if (c) {
      this.nome.set(c.nome);
      this.email.set(c.email);
      this.perfil.set(c.perfil);
      this.cargo.set(c.cargo ?? '');
      this.dataAdmissao.set(c.dataAdmissao);
      this.departamentoId.set(c.departamento?.id ?? null);
      this.gestorId.set(c.gestor?.id ?? null);
      if (c.ativo) {
        this.api.contar({ gestorId: c.id, ativo: true }).subscribe({
          next: (n) => this.equipe.set(n),
          error: () => this.equipe.set(null),
        });
      }
    }
    this.api.gestores().subscribe({ next: (lista) => this.gestores.set(lista), error: () => undefined });
  }

  // ------------------------------------------------------------------ salvar

  protected salvar(): void {
    const dados = this.dados();
    const erros = validar(dados, this.novo());
    this.erros.set(erros);
    this.erroGeral.set(null);
    if (Object.keys(erros).length > 0) {
      // O foco vai para o primeiro campo com erro, para quem usa leitor de tela ouvir o motivo.
      setTimeout(() => this.janela().nativeElement.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus());
      return;
    }
    const c = this.colaborador();
    const chamada = c ? this.api.alterarColaborador(c.id, dados) : this.api.cadastrarColaborador(dados);
    this.executar(chamada, (salvo) => (c ? `Dados de ${salvo.nome} salvos.` : `${salvo.nome} cadastrado.`));
  }

  /** Abre a confirmação e leva o foco (e a rolagem) até ela. */
  protected abrirConfirmacao(): void {
    this.confirmandoDesativar.set(true);
    setTimeout(() => document.getElementById('painel-voltar')?.focus());
  }

  protected fecharConfirmacao(): void {
    this.confirmandoDesativar.set(false);
    setTimeout(() => document.getElementById('painel-desativar')?.focus());
  }

  protected desativar(): void {
    const c = this.colaborador();
    if (c) {
      this.executar(this.api.desativarColaborador(c.id), (salvo) => `Acesso de ${salvo.nome} desativado.`);
    }
  }

  protected reativar(): void {
    const c = this.colaborador();
    if (c) {
      this.executar(this.api.reativarColaborador(c.id), (salvo) => `Acesso de ${salvo.nome} reativado.`);
    }
  }

  private executar(chamada: Observable<Colaborador>, mensagem: (salvo: Colaborador) => string): void {
    this.ocupado.set(true);
    chamada.subscribe({
      next: (salvo) => {
        this.ocupado.set(false);
        this.salvou.emit(mensagem(salvo));
      },
      error: (resposta: HttpErrorResponse) => {
        this.ocupado.set(false);
        const { campos, geral } = errosDaApi(resposta);
        this.erros.set(campos);
        this.erroGeral.set(geral);
        this.confirmandoDesativar.set(false);
      },
    });
  }

  private dados(): DadosDoColaborador {
    return {
      nome: this.nome().trim(),
      email: this.email().trim(),
      ...(this.novo() ? { senha: this.senha() } : {}),
      perfil: this.perfil(),
      cargo: this.cargo().trim() || null,
      dataAdmissao: this.dataAdmissao(),
      departamentoId: this.departamentoId(),
      gestorId: this.gestorId(),
    };
  }

  // ------------------------------------------------------------------ campos

  /** Atualiza o campo e tira o erro antigo dele (a pessoa já está corrigindo). */
  protected mudou(campo: keyof ErrosDoFormulario, alterar: () => void): void {
    alterar();
    if (this.erros()[campo]) {
      this.erros.update(({ [campo]: _, ...resto }) => resto);
    }
  }

  protected numeroOuNulo(valor: string): number | null {
    return valor === '' ? null : Number(valor);
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLInputElement | HTMLSelectElement).value;
  }

  // ------------------------------------------------------------------ teclado

  /** Esc fecha; Tab e Shift+Tab ficam dentro da janela (é modal). */
  protected tecla(evento: KeyboardEvent): void {
    if (evento.key === 'Escape') {
      evento.preventDefault();
      this.fechou.emit();
      return;
    }
    if (evento.key !== 'Tab') {
      return;
    }
    const focaveis = Array.from(
      this.janela().nativeElement.querySelectorAll<HTMLElement>(
        'button:not([disabled]), input:not([disabled]), select:not([disabled]), textarea:not([disabled]), [tabindex="0"]',
      ),
    );
    if (focaveis.length === 0) {
      return;
    }
    const primeiro = focaveis[0];
    const ultimo = focaveis[focaveis.length - 1];
    if (evento.shiftKey && document.activeElement === primeiro) {
      evento.preventDefault();
      ultimo.focus();
    } else if (!evento.shiftKey && document.activeElement === ultimo) {
      evento.preventDefault();
      primeiro.focus();
    }
  }
}
