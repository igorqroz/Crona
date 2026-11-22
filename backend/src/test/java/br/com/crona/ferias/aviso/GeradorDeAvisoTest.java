package br.com.crona.ferias.aviso;

import net.sf.jasperreports.engine.JRPrintElement;
import net.sf.jasperreports.engine.JRPrintText;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Teste do gerador do PDF, sem Spring e sem banco: só o modelo do JasperReports e dados de exemplo.
 * Se o .jrxml tiver erro, o construtor já falha aqui.
 */
class GeradorDeAvisoTest {

    private final GeradorDeAviso gerador = new GeradorDeAviso();

    private final AvisoDeFerias aviso = new AvisoDeFerias("Empresa Demonstração Ltda", "11222333000181",
            "Ana Lima", "Desenvolvedora", "Engenharia", LocalDate.of(2024, 2, 1),
            LocalDate.of(2025, 2, 1), LocalDate.of(2026, 1, 31),
            LocalDate.of(2027, 1, 11), LocalDate.of(2027, 1, 25), 15, 10,
            LocalDate.of(2027, 1, 26), "Roberto Neves", LocalDate.of(2026, 11, 21));

    /** Todos os textos que foram parar no documento (o PDF em si é comprimido; aqui dá para ler). */
    private List<String> textos() {
        var documento = gerador.preencher(aviso);
        return documento.getPages().getFirst().getElements().stream()
                .filter(JRPrintText.class::isInstance)
                .map(JRPrintElement.class::cast)
                .map(e -> ((JRPrintText) e).getFullText())
                .toList();
    }

    @Test
    @DisplayName("gera um PDF de uma página")
    void geraPdf() {
        byte[] pdf = gerador.gerarPdf(aviso);

        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
        assertThat(gerador.preencher(aviso).getPages()).hasSize(1);
    }

    @Test
    @DisplayName("o aviso traz a empresa (CNPJ formatado), o colaborador e as datas no formato brasileiro")
    void conteudo() {
        assertThat(textos()).contains(
                "AVISO DE FÉRIAS",
                "Empresa Demonstração Ltda  ·  CNPJ 11.222.333/0001-81",
                "Ana Lima",
                "01/02/2025 a 31/01/2026",
                "11/01/2027 a 25/01/2027",
                "15 dias corridos",
                "10 dias convertidos em dinheiro",
                "26/01/2027");
    }

    @Test
    @DisplayName("sem abono, sem cargo e sem departamento: textos de reserva, nada de \"null\"")
    void semDadosOpcionais() {
        var semOpcionais = new AvisoDeFerias("Empresa Demo", "11222333000181", "Ana Lima", null, null,
                LocalDate.of(2024, 2, 1), LocalDate.of(2025, 2, 1), LocalDate.of(2026, 1, 31),
                LocalDate.of(2027, 1, 11), LocalDate.of(2027, 1, 25), 15, 0,
                LocalDate.of(2027, 1, 26), null, LocalDate.of(2026, 11, 21));

        var textos = gerador.preencher(semOpcionais).getPages().getFirst().getElements().stream()
                .filter(JRPrintText.class::isInstance)
                .map(e -> ((JRPrintText) e).getFullText())
                .toList();

        assertThat(textos).contains("Não solicitado")
                .anyMatch(t -> t.contains("Cargo não informado") && t.contains("Sem departamento"))
                .noneMatch(t -> t.contains("null"));
    }

    @Test
    @DisplayName("CNPJ: formata os 14 dígitos e deixa como está o que não for CNPJ")
    void cnpj() {
        assertThat(GeradorDeAviso.formatarCnpj("11222333000181")).isEqualTo("11.222.333/0001-81");
        assertThat(GeradorDeAviso.formatarCnpj("123")).isEqualTo("123");
    }
}
