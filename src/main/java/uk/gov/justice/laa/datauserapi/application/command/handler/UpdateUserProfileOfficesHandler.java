package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.OfficeCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Component
public class UpdateUserProfileOfficesHandler implements CommandHandler<UpdateUserProfileOfficesCommand> {

    private final UserProfileCommandRepository userProfileCommandRepository;
    private final OfficeCommandRepository officeCommandRepository;

    public UpdateUserProfileOfficesHandler(
            UserProfileCommandRepository userProfileCommandRepository,
            OfficeCommandRepository officeCommandRepository) {
        this.userProfileCommandRepository = userProfileCommandRepository;
        this.officeCommandRepository = officeCommandRepository;
    }

    @Override
    @Transactional
    public CommandResult handle(UpdateUserProfileOfficesCommand command, String actorOid) {
        UserProfile userProfile = userProfileCommandRepository.findById(command.userProfileId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User profile not found with ID: " + command.userProfileId()));

        Set<Office> offices = new HashSet<>(officeCommandRepository.findAllById(command.officeIds()));
        if (offices.size() != command.officeIds().size()) {
            throw new ResourceNotFoundException("One or more offices were not found");
        }

        UUID firmId = userProfile.getFirm() == null ? null : userProfile.getFirm().getId();
        boolean officeOutsideFirm = offices.stream()
                .anyMatch(office -> firmId == null || !firmId.equals(office.getFirm().getId()));
        if (officeOutsideFirm) {
            return CommandResult.failure("All offices must belong to the user profile's firm");
        }

        userProfile.getOffices().clear();
        userProfile.getOffices().addAll(offices);
        userProfile.setUnrestrictedOfficeAccess(false);
        userProfile.setLastModified(LocalDateTime.now());
        userProfile.setLastModifiedBy(actorOid);
        userProfileCommandRepository.save(userProfile);

        log.info("Updated offices for user profile {} by {}", command.userProfileId(), actorOid);
        return CommandResult.success("Updated offices for user profile " + command.userProfileId());
    }
}
