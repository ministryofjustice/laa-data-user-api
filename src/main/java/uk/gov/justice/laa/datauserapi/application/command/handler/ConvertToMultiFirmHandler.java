package uk.gov.justice.laa.datauserapi.application.command.handler;

import lombok.extern.slf4j.Slf4j;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.ConvertToMultiFirmDomainMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm.ConvertToMultiFirmCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedAppRole;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedUserProfile;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.exception.ConflictException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;
import uk.gov.justice.laa.datauserapi.model.Permission;

import java.util.Objects;
import java.util.Set;

@Slf4j
@Component
public class ConvertToMultiFirmHandler implements CommandHandler<ConvertToMultiFirmCommand, CommandResult> {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final ConvertToMultiFirmDomainMapper mapper;
    private final EntityManager entityManager;

    public ConvertToMultiFirmHandler(EntraUserCommandRepository entraUserCommandRepository,
                                     ConvertToMultiFirmDomainMapper mapper, EntityManager entityManager) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.mapper = mapper;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public CommandResult handle(ConvertToMultiFirmCommand command, AuthenticatedEntraUser actor) {
        log.info("Converting user {} to multi-firm", command.userEntraObjectId());

        requireAuthorisedActor(actor);

        // Refresh a target cached by pre-handler policies after acquiring the row lock.
        EntraUser user = entraUserCommandRepository.findByEntraOidForUpdate(mapper.toEntraOid(command))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found for oid: " + command.userEntraObjectId()));
        entityManager.refresh(user, LockModeType.PESSIMISTIC_WRITE);

        if (user.isMultiFirmUser()) {
            log.warn("User {} is already multi-firm", command.userEntraObjectId());
            throw new ConflictException("User is already a multi-firm user");
        }

        user.convertToMultiFirm(actor.entraOid());
        entraUserCommandRepository.save(user);

        return mapper.toCommandResult(user);
    }

    private void requireAuthorisedActor(AuthenticatedEntraUser actor) {
        AuthenticatedUserProfile profile = actor == null ? null : actor.activeProfile();
        if (profile == null || !profile.activeProfile() || profile.userType() != UserType.INTERNAL) {
            throw new AccessDeniedException("Only internal users can convert a user to multi-firm");
        }
        if (!hasPermission(profile, Permission.EDIT_EXTERNAL_USER)
                || !hasPermission(profile, Permission.CONVERT_USER_TO_MULTI_FIRM)) {
            throw new AccessDeniedException(
                    "Missing EDIT_EXTERNAL_USER or CONVERT_USER_TO_MULTI_FIRM permission");
        }
    }

    private boolean hasPermission(AuthenticatedUserProfile profile, Permission permission) {
        Set<AuthenticatedAppRole> roles = profile.appRoles();
        return roles != null && roles.stream()
                .filter(Objects::nonNull)
                .anyMatch(role -> role.permissions() != null && role.permissions().contains(permission));
    }
}
