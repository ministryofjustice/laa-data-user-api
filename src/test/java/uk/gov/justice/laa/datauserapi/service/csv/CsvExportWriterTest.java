package uk.gov.justice.laa.datauserapi.service.csv;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CsvExportWriterTest {

    @JsonPropertyOrder({"Name", "Email"})
    private record Row(@JsonProperty("Name") String name, @JsonProperty("Email") String email) {
    }

    private final CsvExportWriter writer = new CsvExportWriter();

    @Test
    void write_withNoRows_producesHeaderOnlyCsv() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writer.write(out, Row.class, List.of());

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("Name,Email\n");
    }

    @Test
    void write_withRows_writesHeaderThenEachRowInOrder() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writer.write(out, Row.class, List.of(
                new Row("Ann Example", "ann@example.com"),
                new Row("Bob Example", "bob@example.com")));

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(
                "Name,Email\n"
                        + "Ann Example,ann@example.com\n"
                        + "Bob Example,bob@example.com\n");
    }

    @Test
    void write_escapesValuesContainingCommasAndQuotes() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writer.write(out, Row.class, List.of(new Row("Smith, John \"Jack\"", "john@example.com")));

        assertThat(out.toString(StandardCharsets.UTF_8))
                .isEqualTo("Name,Email\n\"Smith, John \"\"Jack\"\"\",john@example.com\n");
    }

    @ParameterizedTest
    @ValueSource(strings = {"=cmd|'/c calc'!A1", "+1+1", "-1+1", "@SUM(A1:A2)", "\tEvil", "\rEvil"})
    void write_prefixesFormulaTriggeringValuesWithApostrophe(String maliciousValue) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writer.write(out, Row.class, List.of(new Row(maliciousValue, "safe@example.com")));

        String expectedCell = maliciousValue.charAt(0) == '\r'
                ? "\"'" + maliciousValue + "\""
                : "'" + maliciousValue;
        assertThat(out.toString(StandardCharsets.UTF_8))
                .isEqualTo("Name,Email\n" + expectedCell + ",safe@example.com\n");
    }

    @Test
    void write_doesNotAlterOrdinaryValuesOrMidStringFormulaCharacters() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();

        writer.write(out, Row.class, List.of(new Row("Ann = Example", "ann@example.com")));

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("Name,Email\nAnn = Example,ann@example.com\n");
    }

    @Test
    void writeBatches_withZeroMatches_writesHeaderOnlyCsvFromSingleSupplierCall() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Deque<List<Row>> batches = new ArrayDeque<>(List.of(List.of()));

        writer.writeBatches(out, Row.class, batches::poll);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo("Name,Email\n");
    }

    @Test
    void writeBatches_writesEachBatchAsItArrivesUntilSupplierReturnsNull() throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        Deque<List<Row>> batches = new ArrayDeque<>(List.of(
                List.of(new Row("Ann Example", "ann@example.com")),
                List.of(new Row("Bob Example", "bob@example.com"))));

        writer.writeBatches(out, Row.class, batches::poll);

        assertThat(out.toString(StandardCharsets.UTF_8)).isEqualTo(
                "Name,Email\n"
                        + "Ann Example,ann@example.com\n"
                        + "Bob Example,bob@example.com\n");
    }
}
