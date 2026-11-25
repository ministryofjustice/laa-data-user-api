package uk.gov.justice.laa.datauserapi.service.csv;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
}
