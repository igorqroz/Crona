/**
 * P2: a ponte entre o "tom" que as regras devolvem (aprovacoes.regras.ts, trocas.regras.ts) e as
 * classes globais de selo do styles.css. As regras (e os testes delas) continuam falando em
 * "ok", "atencao"...; só a aparência passa a vir das peças únicas.
 */
const CLASSE_DO_TOM: Record<string, string> = {
  ok: 'selo-sucesso',
  sucesso: 'selo-sucesso',
  atencao: 'selo-alerta',
  impedimento: 'selo-perigo',
  acao: 'selo-forte',
  espera: 'selo-info',
  encerrada: '',
};

/** "ok" → "selo selo-sucesso"; um tom desconhecido vira o selo cinza. */
export function classeDoSelo(tom: string): string {
  const classe = CLASSE_DO_TOM[tom] ?? '';
  return classe ? `selo ${classe}` : 'selo';
}
