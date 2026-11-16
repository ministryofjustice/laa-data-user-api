package uk.gov.justice.laa.datauserapi.client.ts;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.exception.MissingEntraOidException;

class EntraOidValidatorTest {
    private final EntraOidValidator validator = new EntraOidValidator();

    @Test
    void acceptsNonUuidEntraIdentifiers() {
        assertThatCode(() -> validator.validateEntraOid("user-123", "test")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullOrBlankIdentifiers() {
        assertThatThrownBy(() -> validator.validateEntraOid(null, "test"))
                .isInstanceOf(MissingEntraOidException.class)
                .hasMessage("Entra OID is required for test.");
        assertThatThrownBy(() -> validator.validateEntraOid("  ", "test"))
                .isInstanceOf(MissingEntraOidException.class)
                .hasMessage("Entra OID is required for test.");
    }
}
