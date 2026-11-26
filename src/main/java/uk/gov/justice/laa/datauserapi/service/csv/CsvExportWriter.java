package uk.gov.justice.laa.datauserapi.service.csv;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.csv.CsvGenerator;
import com.fasterxml.jackson.dataformat.csv.CsvMapper;
import com.fasterxml.jackson.dataformat.csv.CsvSchema;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;
import java.util.function.Supplier;

/**
 * Shared, reusable CSV construction for all exports in this API (not scoped to any single
 * feature, so CSV export schedulers can also depend on it).
 *
 * <p>Holds a single configured {@link CsvMapper}, reused across every export. The column order,
 * header text and per-column value formatting are entirely driven by {@code @JsonPropertyOrder}
 * and {@code @JsonProperty} on the supplied row type; export-specific code only maps its business
 * data to an annotated row record and never constructs CSV infrastructure itself.
 *
 * <p>Writes incrementally to the supplied {@link OutputStream} (no intermediate in-memory CSV
 * string, no temporary files) and writes a header-only CSV when there are no rows.
 */
@Component
public class CsvExportWriter {

    private final CsvMapper csvMapper = CsvMapper.builder()
            .enable(CsvGenerator.Feature.STRICT_CHECK_FOR_QUOTING)
            .addModule(new SimpleModule().addSerializer(String.class, new FormulaInjectionGuard()))
            .build();

    public <T> void write(OutputStream outputStream, Class<T> rowType, List<T> rows) throws IOException {
        CsvSchema schema = csvMapper.schemaFor(rowType).withHeader().withLineSeparator("\n");
        try (var sequenceWriter = csvMapper.writer(schema).writeValues(outputStream)) {
            sequenceWriter.writeAll(rows);
        }
    }

    public <T> void writeBatches(OutputStream outputStream, Class<T> rowType, Supplier<List<T>> batchSupplier)
            throws IOException {
        CsvSchema schema = csvMapper.schemaFor(rowType).withHeader().withLineSeparator("\n");
        try (var sequenceWriter = csvMapper.writer(schema).writeValues(outputStream)) {
            List<T> batch;
            while ((batch = batchSupplier.get()) != null) {
                sequenceWriter.writeAll(batch);
            }
        }
    }

    /**
     * Neutralises CSV/spreadsheet formula injection (a.k.a. CSV injection).
     */
    private static final class FormulaInjectionGuard extends JsonSerializer<String> {

        private static final String FORMULA_TRIGGER_CHARACTERS = "=+-@\t\r";

        @Override
        public void serialize(String value, JsonGenerator gen, SerializerProvider serializers) throws IOException {
            gen.writeString(sanitise(value));
        }

        private static String sanitise(String value) {
            if (!value.isEmpty() && FORMULA_TRIGGER_CHARACTERS.indexOf(value.charAt(0)) >= 0) {
                return "'" + value;
            }
            return value;
        }
    }
}
