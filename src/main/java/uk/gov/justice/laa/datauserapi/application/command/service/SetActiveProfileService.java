package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.SetActiveProfileNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.SetActiveProfileUserNotFoundException;

import java.util.List;

@Slf4j
@Service
public class SetActiveProfileService {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserProfileCommandRepository userProfileCommandRepository;

    public SetActiveProfileService(
            EntraUserCommandRepository entraUserCommandRepository,
            UserProfileCommandRepository userProfileCommandRepository) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.userProfileCommandRepository = userProfileCommandRepository;
    }

    @Transactional
    public CommandResult setActiveProfile(SetActiveProfileCommand command, AuthenticatedEntraUser actor) {
        EntraUser user = entraUserCommandRepository.findByEntraOidForUpdate(command.userEntraObjectId().toString())
                .orElseThrow(SetActiveProfileUserNotFoundException::new);
        List<UserProfile> profiles = userProfileCommandRepository.findAllByEntraUser(user);
        UserProfile requestedProfile = profiles.stream()
                .filter(profile -> profile.getId().equals(command.userProfileId()))
                .findFirst()
                .orElseThrow(SetActiveProfileNotFoundException::new);

        boolean changed = false;
        for (UserProfile profile : profiles) {
            boolean shouldBeActive = profile.getId().equals(requestedProfile.getId());
            if (profile.isActiveProfile() != shouldBeActive) {
                profile.setActiveProfile(shouldBeActive);
                changed = true;
            }
        }
        if (changed) {
            userProfileCommandRepository.saveAll(profiles);
        }

        log.info("Set user active profile",
                kv("userEntraObjectId", command.userEntraObjectId()),
                kv("userProfileId", command.userProfileId()),
                kv("actorEntraOid", actor.entraOid()),
                kv("changed", changed));
        return CommandResult.success("Active profile set successfully");
    }
}
