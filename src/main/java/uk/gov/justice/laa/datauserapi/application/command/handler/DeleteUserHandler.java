package uk.gov.justice.laa.datauserapi.application.command.handler;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.service.RoleChangeNotificationService;
import uk.gov.justice.laa.datauserapi.application.command.service.UserCommandService;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.DeleteUserReasonRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.entity.AppRole;
import uk.gov.justice.laa.datauserapi.entity.DeleteUserReasonEntity;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;
import uk.gov.justice.laa.datauserapi.exception.ReferenceDataNotFoundException;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Component
public class DeleteUserHandler implements CommandHandler<DeleteUserCommand> {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserProfileCommandRepository userProfileCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final DeleteUserReasonRepository deleteUserReasonRepository;
    private final DeleteUserMapper deleteUserMapper;
    private final UserCommandService userCommandService;
    private final RoleChangeNotificationService roleChangeNotificationService;
    private final TechServicesClient techServicesClient;
    private final ModelMapper modelMapper;
    private final EntityManager entityManager;

    public DeleteUserHandler(
            EntraUserCommandRepository entraUserCommandRepository,
            UserProfileCommandRepository userProfileCommandRepository,
            UserAccountStatusAuditRepository auditRepository,
            DeleteUserReasonRepository deleteUserReasonRepository,
            DeleteUserMapper deleteUserMapper,
            UserCommandService userCommandService,
            RoleChangeNotificationService roleChangeNotificationService,
            TechServicesClient techServicesClient,
            ModelMapper modelMapper,
            EntityManager entityManager) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.userProfileCommandRepository = userProfileCommandRepository;
        this.auditRepository = auditRepository;
        this.deleteUserReasonRepository = deleteUserReasonRepository;
        this.deleteUserMapper = deleteUserMapper;
        this.modelMapper = modelMapper;
        this.userCommandService = userCommandService;
        this.roleChangeNotificationService = roleChangeNotificationService;
        this.techServicesClient = techServicesClient;
        this.entityManager = entityManager;
    }

    @Override
    @Transactional
    public CommandResult handle(DeleteUserCommand command, AuthenticatedEntraUser actor) {
        log.info("Handling delete user command for user: {} by actor: {}", command.userEntraObjectId(), actor.entraOid());
        entityManager.clear();

        UUID actorId = UUID.fromString(actor.entraOid());
        if (Objects.equals(actorId, command.userEntraObjectId())) {
            log.warn("User can not delete self: {}", actorId);
            return CommandResult.failure("User can not delete self");
        }

        DeleteUserReasonEntity deleteUserReasonEntity = deleteUserReasonRepository.findByCode(command.deleteReason().name())
                .orElseThrow(() -> new ReferenceDataNotFoundException("Unknown delete reason code: " + command.deleteReason()));

        EntraUser entraUser = entraUserCommandRepository.findByEntraOid(String.valueOf(command.userEntraObjectId()))
                .orElseThrow(() -> new ResourceNotFoundException("User account not found for oid: " + command.userEntraObjectId()));

        if (userCommandService.isInternalUser(command.userEntraObjectId())) {
            log.warn("Internal user can not be deleted: {}, by: {}", command.userEntraObjectId(), actorId);
            return CommandResult.failure("User can not delete internal user");
        }

        if (actor.isExternalUser()) {
            if (entraUser.isMultiFirmUser()) {
                log.warn("User {} can not delete multi-firm user: {}", actorId, command.userEntraObjectId());
                return CommandResult.failure("User can not delete multi-firm user");
            }
            UUID actorFirmId = actor.activeProfile().firmId();
            UUID targetFirmId = userCommandService.getActiveUserProfile(command.userEntraObjectId()).getFirm().getId();
            if (!targetFirmId.equals(actorFirmId)) {
                log.warn("User {} can not delete user {} from different firm", actorId, command.userEntraObjectId());
                return CommandResult.failure("User can not delete user from different firm");
            }
        }

        log.info(
                "Deleting external user. actorEntraId={}, entraUserId={}, reason={}",
                actorId, entraUser.getId(), deleteUserReasonEntity.getCode());

        EntraUserDto userDto = modelMapper.map(entraUser, EntraUserDto.class);
        removeFromTechServices(userDto);

        // audit and result are built first, while the user and its profiles still exist
        UserAccountStatusAudit audit = deleteUserMapper.toAuditEntity(entraUser, command, String.valueOf(actor.id()));
        auditRepository.save(audit);

        removeUserProfiles(entraUser);
        removeEntraUser(entraUser);

        log.info("User account deleted with oid: {} by: {} with reason: {}",
                command.userEntraObjectId(), actor.entraOid(), command.deleteReason());
        return deleteUserMapper.toCommandResult(entraUser);
    }

    /** Tech services failures are logged only, so the local delete always proceeds. */
    private void removeFromTechServices(EntraUserDto userDto) {
        TechServicesApiResponse<ChangeAccountReactivationResponse> deactivation =
                techServicesClient.deactivateUser(userDto, "RoleChangeorNoLongerRequired");
        if (!deactivation.isSuccess()) {
            log.warn("Failed to disable user in tech services: {}, but continuing to delete user",
                    deactivation.getError());
        }

        try {
            techServicesClient.deleteRoleAssignment(userDto);
        } catch (Exception ex) {
            log.warn("Failed to delete role assignment in tech services: {}, but continuing to delete user",
                    ex.getMessage());
        }
    }

    private void removeUserProfiles(EntraUser entraUser) {
        List<UserProfile> profiles = userProfileCommandRepository.findAllByEntraUser(entraUser);
        if (profiles == null || profiles.isEmpty()) {
            return;
        }

        for (UserProfile profile : profiles) {
            removeAssociations(profile);
        }
        // flush so the join table rows (roles, offices) are gone before the profile rows
        userProfileCommandRepository.saveAll(profiles);
        userProfileCommandRepository.flush();
        userProfileCommandRepository.deleteAll(profiles);
        userProfileCommandRepository.flush();
    }

    private void removeAssociations(UserProfile profile) {
        if (profile.getAppRoles() != null) {
            Map<String, Set<String>> puiRoles = filterByPuiRoles(profile.getAppRoles());
            profile.getAppRoles().clear();
            puiRoles.forEach((appEntraOid, ccmsCodes) ->
                    roleChangeNotificationService.sendMessage(profile, appEntraOid, Collections.emptySet(), ccmsCodes));
        }
        if (profile.getOffices() != null) {
            profile.getOffices().clear();
        }
    }

    private void removeEntraUser(EntraUser entraUser) {
        if (entraUser.getUserProfiles() != null) {
            entraUser.setUserProfiles(Set.of());
        }
        entraUserCommandRepository.delete(entraUser);
        entraUserCommandRepository.flush();
    }

    private Map<String, Set<String>> filterByPuiRoles(Set<AppRole> roles) {
        if (roles == null || roles.isEmpty()) {
            return new HashMap<>();
        }

        return roles.stream().filter(AppRole::isLegacySync)
                .filter(ar -> ar.getApp().getEntraOid() != null)
                .collect(Collectors.groupingBy(role -> role.getApp().getEntraOid(),
                        Collectors.collectingAndThen(
                                Collectors.mapping(AppRole::getCcmsCode, Collectors.toSet()),
                                HashSet::new
                        )));
    }

}
