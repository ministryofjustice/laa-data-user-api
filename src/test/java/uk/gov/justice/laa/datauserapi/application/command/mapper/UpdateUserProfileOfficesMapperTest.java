package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.junit.jupiter.api.Test;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesRequest;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesUnrestrictedRequest;

import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class UpdateUserProfileOfficesMapperTest {

    private final UpdateUserProfileOfficesMapper mapper = new UpdateUserProfileOfficesMapper();

    @Test
    void toUpdateUserProfileOfficesCommand_mapsProfileAndOfficeIds() {
        UUID profileId = UUID.randomUUID();
        Set<UUID> officeIds = Set.of(UUID.randomUUID(), UUID.randomUUID());

        UpdateUserProfileOfficesCommand command = mapper.toUpdateUserProfileOfficesCommand(
                new UpdateUserProfileOfficesRequest(profileId, officeIds));

        assertThat(command.userProfileId()).isEqualTo(profileId);
        assertThat(command.officeIds()).isEqualTo(officeIds);
    }

    @Test
    void toUpdateUserProfileUnrestrictedOfficesCommand_mapsProfileAndAccessFlag() {
        UUID profileId = UUID.randomUUID();

        UpdateUserProfileUnrestrictedOfficesCommand command = mapper.toUpdateUserProfileUnrestrictedOfficesCommand(
                new UpdateUserProfileOfficesUnrestrictedRequest(profileId, true));

        assertThat(command.userProfileId()).isEqualTo(profileId);
        assertThat(command.unrestrictedOfficeAccess()).isTrue();
    }
}
