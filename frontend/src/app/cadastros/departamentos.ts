import { NgTemplateOutlet } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { afterNextRender, Component, computed, inject, Injector, signal } from '@angular/core';
import { Observable } from 'rxjs';

import { Icone } from '../compartilhado/icone';
import { DepartamentoResumo } from '../nucleo/modelos';
import { CadastrosApi } from './cadastros-api';
import { avisoDeExclusao, mensagemDoErro, resumo, validarNome } from './departamentos.regras';

/** O que está aberto: o formulário "Novo", ou o "Renomear" ou o "Excluir" de um departamento. */
type Aberto = { tipo: 'novo' } | { tipo: 'renomear' | 'excluir'; id: number };

/**
 * Cadastros > Departamentos (só o RH).
 *
 * Uma lista com criar, renomear e excluir, tudo na própria linha (sem abrir outra tela).
 * Só um formulário fica aberto por vez: abrir um fecha o outro.
 * Acessibilidade: ao abrir um formulário, o foco vai para o campo; ao fechar, volta para o botão
 * que o abriu, para quem usa teclado ou leitor de tela não se perder na página.
 */
@Component({
  selector: 'crona-departamentos',
  imports: [Icone, NgTemplateOutlet],
  templateUrl: './departamentos.html',
  styleUrl: './departamentos.css',
})
export class Departamentos {
  private readonly api = inject(CadastrosApi);
  private readonly injector = inject(Injector);

  protected readonly estado = signal<'carregando' | 'pronto' | 'erro'>('carregando');
  protected readonly departamentos = signal<DepartamentoResumo[]>([]);
  protected readonly total = computed(() => this.departamentos().length);

  /** O que está aberto agora: nada, o "Novo", o "Renomear" de um departamento ou o "Excluir" de um. */
  protected readonly aberto = signal<Aberto | null>(null);
  protected readonly nome = signal('');
  protected readonly erro = signal<string | null>(null);
  protected readonly ocupado = signal(false);
  /** A faixa verde depois de uma ação ("Departamento Pessoas criado."). */
  protected readonly feito = signal<string | null>(null);

  protected readonly resumo = resumo;
  protected readonly avisoDeExclusao = avisoDeExclusao;

  constructor() {
    this.carregar();
  }

  /** recarga = depois de uma ação: a lista continua na tela enquanto a nova chega (sem piscar "Carregando"). */
  protected carregar(recarga = false): void {
    if (!recarga) {
      this.estado.set('carregando');
    }
    this.api.departamentos().subscribe({
      next: (lista) => {
        this.departamentos.set(lista);
        this.estado.set('pronto');
      },
      error: () => this.estado.set('erro'),
    });
  }

  // ------------------------------------------------------------------ abrir e fechar

  protected estaAberto(tipo: 'renomear' | 'excluir', id: number): boolean {
    const a = this.aberto();
    return a !== null && a.tipo === tipo && a.id === id;
  }

  protected abrirNovo(): void {
    this.abrir({ tipo: 'novo' }, '');
    this.focarDepois('campo-novo');
  }

  protected abrirRenomear(departamento: DepartamentoResumo): void {
    this.abrir({ tipo: 'renomear', id: departamento.id }, departamento.nome);
    this.focarDepois(`campo-renomear-${departamento.id}`);
  }

  protected abrirExcluir(departamento: DepartamentoResumo): void {
    this.abrir({ tipo: 'excluir', id: departamento.id }, '');
    // Com gente no departamento, não há botão para confirmar: o foco vai para o aviso.
    this.focarDepois(departamento.pessoas > 0 ? `aviso-excluir-${departamento.id}` : `voltar-excluir-${departamento.id}`);
  }

  /** Fecha o que estiver aberto e devolve o foco para o botão que abriu. */
  protected fechar(): void {
    const a = this.aberto();
    this.aberto.set(null);
    this.erro.set(null);
    if (a?.tipo === 'novo') {
      this.focarDepois('botao-novo');
    } else if (a) {
      this.focarDepois(`${a.tipo}-${a.id}`);
    }
  }

  // ------------------------------------------------------------------ ações

  protected criar(): void {
    const validado = validarNome(this.nome());
    if ('erro' in validado) {
      this.erro.set(validado.erro);
      return;
    }
    this.executar(this.api.criarDepartamento(validado.nome), `Departamento ${validado.nome} criado.`, 'Não foi possível criar o departamento.');
  }

  protected renomear(departamento: DepartamentoResumo): void {
    const validado = validarNome(this.nome());
    if ('erro' in validado) {
      this.erro.set(validado.erro);
      return;
    }
    if (validado.nome === departamento.nome) {
      this.fechar();
      return;
    }
    this.executar(
      this.api.renomearDepartamento(departamento.id, validado.nome),
      `Departamento renomeado para ${validado.nome}.`,
      'Não foi possível renomear o departamento.',
    );
  }

  protected excluir(departamento: DepartamentoResumo): void {
    this.executar(
      this.api.excluirDepartamento(departamento.id),
      `Departamento ${departamento.nome} excluído.`,
      'Não foi possível excluir o departamento.',
    );
  }

  /** A pessoa voltou a digitar: o erro antigo sai, para não ficar acusando um nome que já mudou. */
  protected digitou(texto: string): void {
    this.nome.set(texto);
    this.erro.set(null);
  }

  protected valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }

  // ------------------------------------------------------------------ auxiliares

  private abrir(qual: Aberto, nome: string): void {
    this.aberto.set(qual);
    this.nome.set(nome);
    this.erro.set(null);
    this.feito.set(null);
  }

  /** Chama a API; se der certo, fecha o formulário, mostra a faixa verde e recarrega a lista. */
  private executar(chamada: Observable<unknown>, sucesso: string, generica: string): void {
    this.ocupado.set(true);
    this.erro.set(null);
    chamada.subscribe({
      next: () => {
        this.ocupado.set(false);
        this.aberto.set(null);
        this.feito.set(sucesso);
        this.carregar(true);
        // O foco vai para a faixa de sucesso: o leitor de tela lê o que aconteceu.
        this.focarDepois('feito');
      },
      error: (resposta: HttpErrorResponse) => {
        this.ocupado.set(false);
        this.erro.set(mensagemDoErro(resposta, generica));
      },
    });
  }

  /** Foca um elemento pelo id depois que o Angular desenhar a tela (ele ainda não existe agora). */
  private focarDepois(id: string): void {
    afterNextRender(() => document.getElementById(id)?.focus(), { injector: this.injector });
  }
}
