package br.com.crona.empresa;

import br.com.crona.compartilhado.EntidadeBase;
import br.com.crona.compartilhado.erro.RegraVioladaException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;

/**
 * As regras configuráveis de uma empresa (EMP-01, 03, 04 e 06). Valor nulo = regra desligada.
 */
@Entity
@Table(name = "configuracao_ferias")
public class ConfiguracaoFerias extends EntidadeBase {

    // 1:1 com a empresa: cada empresa tem exatamente uma configuração.
    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "empresa_id", nullable = false, unique = true, updatable = false)
    private Empresa empresa;

    @Column(name = "percentual_minimo_trabalhando")
    private Integer percentualMinimoTrabalhando;

    @Column(name = "limite_ferias_simultaneas_empresa")
    private Integer limiteFeriasSimultaneasEmpresa;

    @Column(name = "antecedencia_minima_dias", nullable = false)
    private int antecedenciaMinimaDias = 30;

    @Column(name = "exige_confirmacao_rh", nullable = false)
    private boolean exigeConfirmacaoRh;

    protected ConfiguracaoFerias() {
    }

    /** Uma empresa sem configuração gravada usa os padrões: só a antecedência de 30 dias ligada. */
    public static ConfiguracaoFerias padrao() {
        return new ConfiguracaoFerias();
    }

    /** Cadastros 3: a primeira vez que o RH salva as regras de uma empresa que ainda usava o padrão. */
    public static ConfiguracaoFerias daEmpresa(Empresa empresa) {
        var configuracao = new ConfiguracaoFerias();
        configuracao.empresa = empresa;
        return configuracao;
    }

    /**
     * Cadastros 3: o RH muda as regras. Os limites também estão no banco (CHECK em V1) e na
     * requisição (@Valid); aqui a entidade garante sozinha o que é regra de negócio.
     */
    public void alterar(Integer percentualMinimoTrabalhando, int antecedenciaMinimaDias,
                        Integer limiteFeriasSimultaneasEmpresa, boolean exigeConfirmacaoRh) {
        if (percentualMinimoTrabalhando != null
                && (percentualMinimoTrabalhando < 0 || percentualMinimoTrabalhando > 100)) {
            throw new RegraVioladaException("O percentual que continua trabalhando vai de 0 a 100.");
        }
        if (antecedenciaMinimaDias < 30) {
            throw new RegraVioladaException("A antecedência mínima não pode ser menor que 30 dias (CLT, art. 135).");
        }
        if (limiteFeriasSimultaneasEmpresa != null && limiteFeriasSimultaneasEmpresa < 1) {
            throw new RegraVioladaException("O limite da empresa precisa ser de pelo menos 1 pessoa.");
        }
        this.percentualMinimoTrabalhando = percentualMinimoTrabalhando;
        this.antecedenciaMinimaDias = antecedenciaMinimaDias;
        this.limiteFeriasSimultaneasEmpresa = limiteFeriasSimultaneasEmpresa;
        this.exigeConfirmacaoRh = exigeConfirmacaoRh;
    }

    public Integer getPercentualMinimoTrabalhando() {
        return percentualMinimoTrabalhando;
    }

    public Integer getLimiteFeriasSimultaneasEmpresa() {
        return limiteFeriasSimultaneasEmpresa;
    }

    public int getAntecedenciaMinimaDias() {
        return antecedenciaMinimaDias;
    }

    public boolean isExigeConfirmacaoRh() {
        return exigeConfirmacaoRh;
    }
}
