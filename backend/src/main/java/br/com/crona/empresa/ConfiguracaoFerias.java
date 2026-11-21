package br.com.crona.empresa;

import br.com.crona.compartilhado.EntidadeBase;
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
