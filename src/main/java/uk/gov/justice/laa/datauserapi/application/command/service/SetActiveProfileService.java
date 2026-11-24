package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authorization.AuthorizationDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.dto.SetActiveProfileContext;
import uk.gov.justice.laa.datauserapi.application.command.policy.SetActiveProfilePolicy;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.SetActiveProfileCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.UserNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.UserProfileNotFoundException;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyDecision;
import uk.gov.justice.laa.datauserapi.security.rbac.PolicyAuthorizationDenial;

import java.util.List;

@Slf4j
@Service
public class SetActiveProfileService {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserProfileCommandRepository userProfileCommandRepository;
    private final EntityManager entityManager;
    private final SetActiveProfilePolicy policy;

    public SetActiveProfileService(
            EntraUserCommandRepository entraUserCommandRepository,
            UserProfileCommandRepository userProfileCommandRepository,
            EntityManager entityManager,
            SetActiveProfilePolicy policy) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.userProfileCommandRepository = userProfileCommandRepository;
        this.entityManager = entityManager;
        this.policy = policy;
    }

    @Transactional
    public CommandResult setActiveProfile(SetActiveProfileCommand command, AuthenticatedEntraUser actor) {
        EntraUser user = entraUserCommandRepository.findByEntraOidForUpdate(command.userEntraObjectId().toString())
                .orElseThrow(() -> new UserNotFoundException(command.userEntraObjectId()));
        List<UserProfile> profiles = userProfileCommandRepository.findRefreshProfilesForUpdate(List.of(user.getId()));
        // The request-scoped persistence context may contain profiles read before the locks were acquired.
        profiles.forEach(profile -> entityManager.refresh(profile, LockModeType.PESSIMISTIC_WRITE));
        UserProfile requestedProfile = profiles.stream()
                .filter(profile -> profile.getId().equals(command.userProfileId())
                        && profile.getEntraUser().getId().equals(user.getId()))
                .findFirst()
                .orElseThrow(() -> new UserProfileNotFoundException(command.userProfileId()));

        SetActiveProfileContext context = new SetActiveProfileContext(
                true, requestedProfile.getId(), user.getId(), true, requestedProfile.getUserType());
        boolean activeExternal = profiles.stream().anyMatch(profile -> profile.isActiveProfile()
                && profile.getUserType() == UserType.EXTERNAL
                && profile.getEntraUser().getId().equals(user.getId()));
        PolicyDecision decision = policy.evaluateCurrentState(context, actor, activeExternal);
        if (!decision.granted()) {
            throw new AuthorizationDeniedException("Active profile selection denied",
                    new PolicyAuthorizationDenial(decision.reason()));
        }

        boolean changed = false;
        for (UserProfile profile : profiles) {
            if (!profile.getEntraUser().getId().equals(user.getId())) {
                continue;
            }
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
