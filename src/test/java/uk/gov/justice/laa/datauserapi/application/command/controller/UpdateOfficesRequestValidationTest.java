package uk.gov.justice.laa.datauserapi.application.command.controller;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateOfficesUnrestrictedCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesRequest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateOfficesRequestValidationTest {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void officeAssignmentsRequest_requiresProfileIdAndOfficeIdsButAllowsAnEmptySet() {
        assertThat(validator.validate(new UpdateUserProfileOfficesRequest(null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("userProfileId", "officeIds");
        assertThat(validator.validate(new UpdateUserProfileOfficesRequest(UUID.randomUUID(), Set.of())))
                .isEmpty();
    }

    @Test
    void unrestrictedAccessRequest_requiresProfileIdAndAccessFlag() {
        assertThat(validator.validate(new UpdateOfficesUnrestrictedCommand(null, null)))
                .extracting(violation -> violation.getPropertyPath().toString())
                .containsExactlyInAnyOrder("userProfileId", "unrestrictedOfficeAccess");
        assertThat(validator.validate(new UpdateOfficesUnrestrictedCommand(UUID.randomUUID(), false)))
                .isEmpty();
    }
}
