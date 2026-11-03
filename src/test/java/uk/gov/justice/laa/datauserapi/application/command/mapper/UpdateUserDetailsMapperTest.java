package uk.gov.justice.laa.datauserapi.application.command.mapper;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.UpdateUserDetailsCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserDetailsRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateUserDetailsMapperTest {

    private final UpdateUserDetailsMapper mapper = new UpdateUserDetailsMapper();
    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void trimsValuesAndKeepsNulls() {
        UUID id = UUID.randomUUID();

        UpdateUserDetailsCommand command = mapper.toUpdateUserDetailsCommand(
                new UpdateUserDetailsRequest(id, " John ", null, " j@d.com "));

        assertThat(command.userEntraObjectId()).isEqualTo(id);
        assertThat(command.firstName()).isEqualTo("John");
        assertThat(command.lastName()).isNull();
        assertThat(command.email()).isEqualTo("j@d.com");
    }

    @Test
    void buildsSuccessResult() {
        CommandResult result = mapper.toCommandResult(EntraUser.builder().entraOid("oid").build());

        assertThat(result.success()).isTrue();
        assertThat(result.message()).isEqualTo("User account 'oid' details updated successfully");
    }

    @Test
    void requestRequiresAtLeastOneDetail() {
        assertThat(validator.validate(new UpdateUserDetailsRequest(UUID.randomUUID(), null, null, null))).hasSize(1);
        assertThat(validator.validate(new UpdateUserDetailsRequest(UUID.randomUUID(), "A", null, null))).isEmpty();
    }

    @Test
    void requestRejectsBlankNamesBadEmailAndMissingId() {
        assertThat(validator.validate(new UpdateUserDetailsRequest(UUID.randomUUID(), " ", null, null))).isNotEmpty();
        assertThat(validator.validate(new UpdateUserDetailsRequest(UUID.randomUUID(), null, null, "not-an-email"))).isNotEmpty();
        assertThat(validator.validate(new UpdateUserDetailsRequest(null, "A", null, null))).isNotEmpty();
    }
}
