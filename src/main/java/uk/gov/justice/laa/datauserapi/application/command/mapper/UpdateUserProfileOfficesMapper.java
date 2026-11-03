package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateOfficesUnrestrictedCommand;
import uk.gov.justice.laa.datauserapi.contracts.request.UpdateUserProfileOfficesRequest;

@Component
public class UpdateUserProfileOfficesMapper {

    public UpdateUserProfileOfficesCommand toUpdateUserProfileOfficesCommand(UpdateUserProfileOfficesRequest request) {
        return new UpdateUserProfileOfficesCommand(request.userProfileId(), request.officeIds());
    }

    public UpdateUserProfileUnrestrictedOfficesCommand toUpdateUserProfileUnrestrictedOfficesCommand(UpdateOfficesUnrestrictedCommand unrestrictedRequest) {
        return new UpdateUserProfileUnrestrictedOfficesCommand(unrestrictedRequest.userProfileId(), unrestrictedRequest.unrestrictedOfficeAccess());
    }
}
