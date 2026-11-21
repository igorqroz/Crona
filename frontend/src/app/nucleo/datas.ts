/*
 * Ajudantes de data.
 *
 * Cuidado clássico: new Date("2026-03-17") é lido como meia-noite em UTC, que no Brasil
 * (UTC-3) ainda é dia 16! Por isso as datas sem hora da API passam por dataLocal(), que
 * monta a data no fuso do navegador.
 */

export function dataLocal(iso: string): Date {
  const [ano, mes, dia] = iso.slice(0, 10).split('-').map(Number);
  return new Date(ano, mes - 1, dia);
}

/** "2026-03-17" → "17/03" */
export function diaMes(iso: string): string {
  return `${iso.slice(8, 10)}/${iso.slice(5, 7)}`;
}

/** "2026-03-17" → "17/03/2026" */
export function diaMesAno(iso: string): string {
  return `${diaMes(iso)}/${iso.slice(0, 4)}`;
}

/** "17/03 – 31/03" */
export function intervalo(inicio: string, fim: string): string {
  return `${diaMes(inicio)} – ${diaMes(fim)}`;
}

/** Um instante (com hora) → "21/11, 08:12" */
export function diaMesHora(instante: string): string {
  return new Intl.DateTimeFormat('pt-BR', {
    day: '2-digit',
    month: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
  }).format(new Date(instante));
}

/** "Sábado, 21 de novembro" */
export function dataPorExtenso(data: Date): string {
  const texto = new Intl.DateTimeFormat('pt-BR', { weekday: 'long', day: 'numeric', month: 'long' }).format(data);
  return texto.charAt(0).toUpperCase() + texto.slice(1);
}

/** "há 4 horas", "ontem", "há 3 dias" */
export function haQuanto(instante: string, agora: Date = new Date()): string {
  const segundos = Math.round((new Date(instante).getTime() - agora.getTime()) / 1000);
  const formato = new Intl.RelativeTimeFormat('pt-BR', { numeric: 'auto' });
  const passos: [Intl.RelativeTimeFormatUnit, number][] = [
    ['day', 86400],
    ['hour', 3600],
    ['minute', 60],
  ];
  for (const [unidade, tamanho] of passos) {
    if (Math.abs(segundos) >= tamanho) {
      return formato.format(Math.round(segundos / tamanho), unidade);
    }
  }
  return 'agora';
}
