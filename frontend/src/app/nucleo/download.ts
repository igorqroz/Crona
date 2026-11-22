/**
 * Salva um arquivo que veio da API (um Blob) com o nome escolhido.
 *
 * Por que não um simples <a href="/api/...">? Porque um link normal não manda o token:
 * a API responderia 401. Então o arquivo vem pelo HttpClient (que passa pelo interceptor)
 * e aqui a gente cria um link temporário, "clica" nele e o descarta.
 */
export function baixarArquivo(arquivo: Blob, nome: string): void {
  const url = URL.createObjectURL(arquivo);
  const link = document.createElement('a');
  link.href = url;
  link.download = nome;
  link.click();
  URL.revokeObjectURL(url); // libera a memória do arquivo
}
