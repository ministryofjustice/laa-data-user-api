package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.OfficeCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileOfficesCommand;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.UpdateUserProfileUnrestrictedOfficesCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.Office;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;

import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

@Slf4j
@Service
public class UserProfileOfficeService {

    private final UserProfileCommandRepository userProfileCommandRepository;
    private final OfficeCommandRepository officeCommandRepository;

    public UserProfileOfficeService(
            UserProfileCommandRepository userProfileCommandRepository,
            OfficeCommandRepository officeCommandRepository) {
        this.userProfileCommandRepository = userProfileCommandRepository;
        this.officeCommandRepository = officeCommandRepository;
    }

    @Transactional
    public CommandResult updateOffices(UpdateUserProfileOfficesCommand command, AuthenticatedEntraUser actor) {
        UserProfile userProfile = loadProfile(command.userProfileId());

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
        saveProfile(userProfile, actor);

        log.info("Updated user profile offices",
                kv("userProfileId", command.userProfileId()),
                kv("actorEntraOid", actor.entraOid()),
                kv("officeIds", command.officeIds()));
        return CommandResult.success("Updated offices for user profile " + command.userProfileId());
    }

    @Transactional
    public CommandResult updateUnrestrictedAccess(
            UpdateUserProfileUnrestrictedOfficesCommand command, AuthenticatedEntraUser actor) {
        UserProfile userProfile = loadProfile(command.userProfileId());

        userProfile.setUnrestrictedOfficeAccess(command.unrestrictedOfficeAccess());
        userProfile.getOffices().clear();
        saveProfile(userProfile, actor);

        log.info("Updated unrestricted office access",
                kv("unrestrictedOfficeAccess", command.unrestrictedOfficeAccess()),
                kv("userProfileId", command.userProfileId()),
                kv("actorEntraOid", actor.entraOid()));
        return CommandResult.success("Updated unrestricted office access for user profile " + command.userProfileId());
    }

    private UserProfile loadProfile(UUID profileId) {
        return userProfileCommandRepository.findById(profileId)
                .orElseThrow(() -> new UserProfileNotFoundException(profileId));
    }

    private void saveProfile(UserProfile userProfile, AuthenticatedEntraUser actor) {
        userProfile.setLastModified(LocalDateTime.now());
        userProfile.setLastModifiedBy(String.valueOf(actor.id()));
        userProfileCommandRepository.save(userProfile);
    }
}
