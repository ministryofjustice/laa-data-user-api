package uk.gov.justice.laa.datauserapi.contracts.dto;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AuditUserExportCriteriaValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void unconstrainedRequest_withNoFirmIdAndNoInternalUserType_isInvalid() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(null, null, null, null, null, null, null);

        assertThat(validator.validate(criteria)).isNotEmpty();
    }

    @Test
    void internalOnlyRequest_withFirmId_isInvalid() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, "FIRM001", null, null, null, null);

        assertThat(validator.validate(criteria)).isNotEmpty();
    }

    @Test
    void internalOnlyRequest_withNoFirmId_isValid() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);

        Set<ConstraintViolation<AuditUserExportCriteria>> violations = validator.validate(criteria);

        assertThat(violations).isEmpty();
    }

    @Test
    void internalOnlyRequest_withEmptyFirmId_isInvalid() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, "", null, null, null, null);

        assertThat(validator.validate(criteria)).isNotEmpty();
    }

    @Test
    void internalOnlyRequest_withWhitespaceOnlyFirmId_isInvalid() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.INTERNAL, null, "   ", null, null, null, null);

        assertThat(validator.validate(criteria)).isNotEmpty();
    }

    @Test
    void firmScopedRequest_withFirmIdAndNonInternalUserType_isValid() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.EXTERNAL, null, "FIRM001", null, null, null, null);

        assertThat(validator.validate(criteria)).isEmpty();
    }

    @Test
    void firmScopedRequest_withBlankFirmId_isInvalid() {
        AuditUserExportCriteria criteria =
                new AuditUserExportCriteria(UserType.EXTERNAL, null, "   ", null, null, null, null);

        assertThat(validator.validate(criteria)).isNotEmpty();
    }
}
