package br.com.crona.empresa;

import br.com.crona.compartilhado.EntidadeBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Cada cliente do Crona. Quase todos os outros dados pertencem a uma empresa.
 */
@Entity
@Table(name = "empresa")
public class Empresa extends EntidadeBase {

    @Column(name = "razao_social", nullable = false, length = 200)
    private String razaoSocial;

    @Column(name = "nome_fantasia", length = 200)
    private String nomeFantasia;

    // No banco é CHAR(14), não VARCHAR. Sem isto, o "validate" do Hibernate
    // reclama que o tipo da coluna não bate com o da classe.
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "cnpj", nullable = false, length = 14, unique = true)
    private String cnpj;

    // O JPA precisa de um construtor sem argumentos para montar o objeto
    // a partir do banco. "protected" impede o resto do código de usá-lo.
    protected Empresa() {
    }

    public String getRazaoSocial() {
        return razaoSocial;
    }

    public String getNomeFantasia() {
        return nomeFantasia;
    }

    public String getCnpj() {
        return cnpj;
    }
}
