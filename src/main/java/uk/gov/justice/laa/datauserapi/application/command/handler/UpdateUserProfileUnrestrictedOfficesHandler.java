package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.time.LocalDateTime;

@Slf4j
@Component
public class UpdateUserProfileUnrestrictedOfficesHandler
        implements CommandHandler<UpdateUserProfileUnrestrictedOfficesCommand> {

    private final UserProfileCommandRepository userProfileCommandRepository;

    public UpdateUserProfileUnrestrictedOfficesHandler(UserProfileCommandRepository userProfileCommandRepository) {
        this.userProfileCommandRepository = userProfileCommandRepository;
    }

    @Override
    @Transactional
    public CommandResult handle(UpdateUserProfileUnrestrictedOfficesCommand command, String actorOid) {
        UserProfile userProfile = userProfileCommandRepository.findById(command.userProfileId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User profile not found with ID: " + command.userProfileId()));

        userProfile.setUnrestrictedOfficeAccess(command.unrestrictedOfficeAccess());
        userProfile.getOffices().clear();
        userProfile.setLastModified(LocalDateTime.now());
        userProfile.setLastModifiedBy(actorOid);
        userProfileCommandRepository.save(userProfile);

        log.info("Updated unrestricted office access to {} for user profile {} by {}",
                command.unrestrictedOfficeAccess(), command.userProfileId(), actorOid);
        return CommandResult.success("Updated unrestricted office access for user profile " + command.userProfileId());
    }
}
