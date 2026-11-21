package br.com.crona.ferias.validacao;

import java.time.LocalDate;
import java.util.List;

/**
 * O resultado completo: as datas calculadas e todas as verificações, inclusive as que passaram
 * (a tela mostra os ✅ também, para o colaborador saber que está tudo certo).
 */
public record ResultadoDaValidacao(LocalDate inicio, LocalDate fim, LocalDate voltaAoTrabalho,
                                   List<Verificacao> verificacoes) {

    /** Só os impedimentos travam o envio. Pontos de atenção não. */
    public boolean podeEnviar() {
        return verificacoes.stream().noneMatch(v -> v.resultado() == Verificacao.Resultado.IMPEDIMENTO);
    }

    public List<Verificacao> com(Verificacao.Resultado resultado) {
        return verificacoes.stream().filter(v -> v.resultado() == resultado).toList();
    }

    /** A verificação de uma regra específica (para os testes e para a tela). */
    public Verificacao daRegra(String regra) {
        return verificacoes.stream().filter(v -> v.regra().equals(regra)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Nenhuma verificação da regra " + regra));
    }
}
