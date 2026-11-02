package uk.gov.justice.laa.datauserapi.client.ts;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;

class UserCommandValidatorTest {
    private final UserCommandValidator validator = new UserCommandValidator();

    @Test
    void acceptsNonUuidEntraIdentifiers() {
        assertThatCode(() -> validator.validateEntraOid("user-123", "test")).doesNotThrowAnyException();
    }

    @Test
    void rejectsNullOrBlankIdentifiers() {
        assertThatThrownBy(() -> validator.validateEntraOid(null, "test"))
                .isInstanceOf(TechServicesClientException.class);
        assertThatThrownBy(() -> validator.validateEntraOid("  ", "test"))
                .isInstanceOf(TechServicesClientException.class);
    }
}
