import { Component, computed, effect, input, output, signal, untracked } from '@angular/core';

import { Avatar } from '../../compartilhado/avatar';
import { Checklist } from '../../compartilhado/checklist';
import { Icone } from '../../compartilhado/icone';
import { classeDoSelo } from '../../compartilhado/selo';
import { haQuanto } from '../../nucleo/datas';
import { Contraproposta, Verificacao } from '../../nucleo/modelos';
import { paraContraproposta, VisaoDaTroca } from './trocas.regras';

export interface ErroDaAcao {
  mensagem: string;
  verificacoes: Verificacao[];
}

/**
 * Um cartão de negociação. Mostra as datas do ponto de vista de quem está logado e os botões
 * que fazem sentido agora. Os formulários pequenos (contrapropor, recusar, cancelar) ficam aqui
 * dentro; a página só recebe o resultado pelos output() e chama a API.
 */
@Component({
  selector: 'crona-cartao-troca',
  imports: [Avatar, Checklist, Icone],
  templateUrl: './cartao-troca.html',
  styleUrl: './cartao-troca.css',
})
export class CartaoTroca {
  readonly visao = input.required<VisaoDaTroca>();
  /** Uma ação deste cartão está indo para a API: desabilita os botões. */
  readonly ocupado = input(false);
  readonly erro = input<ErroDaAcao | null>(null);

  readonly aceitou = output<void>();
  readonly recusou = output<string | undefined>();
  readonly cancelou = output<void>();
  readonly contrapropos = output<Contraproposta>();

  /** Qual formulário pequeno está aberto. */
  protected readonly modo = signal<'nenhum' | 'contrapropor' | 'recusar' | 'cancelar'>('nenhum');
  protected readonly meuInicio = signal('');
  protected readonly inicioDoOutro = signal('');
  protected readonly mensagem = signal('');

  protected readonly haQuanto = haQuanto;
  protected readonly classeDoSelo = classeDoSelo;
  protected readonly amanha = amanhaIso();

  /**
   * BUG-04: o que identifica um MOMENTO da negociação. A página recarrega a lista inteira depois de
   * qualquer ação, então todos os cartões recebem um objeto novo. Só muda esta chave o cartão cuja
   * negociação andou (status, a vez ou uma rodada nova). Como um computed só avisa quando o valor
   * muda, os outros cartões não são avisados e o formulário aberto neles continua aberto.
   */
  private readonly momento = computed(() => {
    const v = this.visao();
    return `${v.id}|${v.status}|${v.minhaVez}|${v.rodadas.length}`;
  });

  constructor() {
    // A negociação DESTE cartão andou (a ação deu certo): o formulário aberto fecha.
    // Se a API recusar, nada muda: o formulário fica, com o que foi digitado.
    // untracked(): só o momento dispara o effect; ler/escrever o modo aqui não cria dependência.
    effect(() => {
      this.momento();
      untracked(() => this.modo.set('nenhum'));
    });
  }

  protected abrir(modo: 'contrapropor' | 'recusar' | 'cancelar'): void {
    // A contraproposta começa com as datas da proposta atual: a pessoa só ajusta.
    this.meuInicio.set(this.visao().meuInicioProposto);
    this.inicioDoOutro.set(this.visao().inicioDoOutroProposto);
    this.mensagem.set('');
    this.modo.set(modo);
  }

  protected fechar(): void {
    this.modo.set('nenhum');
  }

  protected enviarContraproposta(): void {
    const mensagem = this.mensagem().trim() || undefined;
    this.contrapropos.emit(
      paraContraproposta(this.visao().souQuemPropos, this.meuInicio(), this.inicioDoOutro(), mensagem),
    );
  }

  protected confirmarRecusa(): void {
    this.recusou.emit(this.mensagem().trim() || undefined);
  }

  /** O valor digitado num <input> ou <textarea> (para os eventos do template). */
  protected valor(evento: Event): string {
    return (evento.target as HTMLInputElement).value;
  }
}

function amanhaIso(): string {
  const amanha = new Date();
  amanha.setDate(amanha.getDate() + 1);
  return amanha.toLocaleDateString('sv-SE');
}
