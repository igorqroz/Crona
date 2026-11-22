package br.com.crona.ferias.aviso;

import net.sf.jasperreports.engine.JREmptyDataSource;
import net.sf.jasperreports.engine.JRException;
import net.sf.jasperreports.engine.JasperCompileManager;
import net.sf.jasperreports.engine.JasperExportManager;
import net.sf.jasperreports.engine.JasperFillManager;
import net.sf.jasperreports.engine.JasperPrint;
import net.sf.jasperreports.engine.JasperReport;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

/**
 * Transforma um {@link AvisoDeFerias} em PDF, com o JasperReports.
 *
 * O caminho do JasperReports tem 3 passos:
 * 1. COMPILAR o modelo (.jrxml → JasperReport). É o passo caro, então é feito UMA vez só,
 *    no construtor, e o resultado fica guardado;
 * 2. PREENCHER (fill) o modelo com os dados → JasperPrint (o documento pronto, em memória);
 * 3. EXPORTAR o JasperPrint para PDF.
 *
 * Atenção para o deploy (Fase 9): compilar o .jrxml usa o compilador do Java em tempo de execução,
 * então a imagem precisa de um JDK (e não só de um JRE) — ou o modelo é pré-compilado no build.
 */
@Component
public class GeradorDeAviso {

    private static final String MODELO = "/relatorios/aviso-de-ferias.jrxml";
    private static final DateTimeFormatter DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final JasperReport modelo;

    public GeradorDeAviso() {
        try (InputStream jrxml = GeradorDeAviso.class.getResourceAsStream(MODELO)) {
            if (jrxml == null) {
                throw new IllegalStateException("Modelo do aviso não encontrado: " + MODELO);
            }
            this.modelo = JasperCompileManager.compileReport(jrxml);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        } catch (JRException e) {
            throw new IllegalStateException("O modelo do aviso de férias tem um erro: " + e.getMessage(), e);
        }
    }

    /** O documento preenchido, ainda em memória (os testes olham os textos daqui). */
    JasperPrint preencher(AvisoDeFerias aviso) {
        try {
            // JREmptyDataSource(1): o relatório não tem lista de linhas, só parâmetros; "1" = uma página.
            return JasperFillManager.fillReport(modelo, parametros(aviso), new JREmptyDataSource(1));
        } catch (JRException e) {
            throw new IllegalStateException("Não foi possível montar o aviso de férias.", e);
        }
    }

    public byte[] gerarPdf(AvisoDeFerias aviso) {
        try {
            return JasperExportManager.exportReportToPdf(preencher(aviso));
        } catch (JRException e) {
            throw new IllegalStateException("Não foi possível gerar o PDF do aviso de férias.", e);
        }
    }

    /** Os textos já formatados: o modelo só posiciona, não formata nada. */
    private static Map<String, Object> parametros(AvisoDeFerias a) {
        Map<String, Object> p = new HashMap<>();
        p.put("razaoSocial", a.razaoSocial());
        p.put("cnpj", formatarCnpj(a.cnpj()));
        p.put("colaborador", a.colaborador());
        p.put("cargo", textoOu(a.cargo(), "Cargo não informado"));
        p.put("departamento", textoOu(a.departamento(), "Sem departamento"));
        p.put("admissao", data(a.admissao()));
        p.put("periodoAquisitivo", data(a.inicioAquisitivo()) + " a " + data(a.fimAquisitivo()));
        p.put("gozo", data(a.inicio()) + " a " + data(a.fim()));
        p.put("dias", a.diasGozo() + " dias corridos");
        p.put("abono", a.diasAbono() == 0 ? "Não solicitado" : a.diasAbono() + " dias convertidos em dinheiro");
        p.put("voltaAoTrabalho", data(a.voltaAoTrabalho()));
        p.put("aprovadoPor", textoOu(a.aprovadoPor(), "gestor"));
        p.put("emitidoEm", data(a.emitidoEm()));
        return p;
    }

    /** "11222333000181" → "11.222.333/0001-81" */
    static String formatarCnpj(String cnpj) {
        if (cnpj == null || !cnpj.matches("\\d{14}")) {
            return cnpj;
        }
        return cnpj.replaceFirst("(\\d{2})(\\d{3})(\\d{3})(\\d{4})(\\d{2})", "$1.$2.$3/$4-$5");
    }

    private static String data(LocalDate data) {
        return data == null ? "-" : data.format(DATA);
    }

    private static String textoOu(String texto, String padrao) {
        return texto == null || texto.isBlank() ? padrao : texto;
    }
}
