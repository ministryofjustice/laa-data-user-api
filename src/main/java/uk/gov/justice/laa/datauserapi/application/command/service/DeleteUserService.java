package uk.gov.justice.laa.datauserapi.application.command.service;

import static net.logstash.logback.argument.StructuredArguments.kv;

import jakarta.persistence.EntityManager;
import lombok.extern.slf4j.Slf4j;
import org.modelmapper.ModelMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.mapper.DeleteUserMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.DeleteUserReasonRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserAccountStatusAuditRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.UserProfileCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
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
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Coordinates local account deletion, audit persistence, and cleanup of the user's profiles.
 * External Tech Services effects are isolated in {@link DeleteUserTechServicesService}.
 */
@Slf4j
@Service
public class DeleteUserService {

    private final EntraUserCommandRepository entraUserCommandRepository;
    private final UserProfileCommandRepository userProfileCommandRepository;
    private final UserAccountStatusAuditRepository auditRepository;
    private final DeleteUserReasonRepository deleteUserReasonRepository;
    private final DeleteUserMapper deleteUserMapper;
    private final RoleChangeNotificationService roleChangeNotificationService;
    private final DeleteUserTechServicesService deleteUserTechServicesService;
    private final ModelMapper modelMapper;
    private final EntityManager entityManager;

    public DeleteUserService(
            EntraUserCommandRepository entraUserCommandRepository,
            UserProfileCommandRepository userProfileCommandRepository,
            UserAccountStatusAuditRepository auditRepository,
            DeleteUserReasonRepository deleteUserReasonRepository,
            DeleteUserMapper deleteUserMapper,
            RoleChangeNotificationService roleChangeNotificationService,
            DeleteUserTechServicesService deleteUserTechServicesService,
            ModelMapper modelMapper,
            EntityManager entityManager) {
        this.entraUserCommandRepository = entraUserCommandRepository;
        this.userProfileCommandRepository = userProfileCommandRepository;
        this.auditRepository = auditRepository;
        this.deleteUserReasonRepository = deleteUserReasonRepository;
        this.deleteUserMapper = deleteUserMapper;
        this.roleChangeNotificationService = roleChangeNotificationService;
        this.deleteUserTechServicesService = deleteUserTechServicesService;
        this.modelMapper = modelMapper;
        this.entityManager = entityManager;
    }

    @Transactional
    public CommandResult delete(DeleteUserCommand command, AuthenticatedEntraUser actor) {
        log.info("Handling delete user command",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()));
        entityManager.clear();

        DeleteUserReasonEntity reason = deleteUserReasonRepository.findByCode(command.deleteReason().name())
                .orElseThrow(() -> new ReferenceDataNotFoundException(
                        "Unknown delete reason code: " + command.deleteReason()));
        EntraUser entraUser = entraUserCommandRepository.findByEntraOid(String.valueOf(command.userEntraObjectId()))
                .orElseThrow(() -> new ResourceNotFoundException(
                        "User account not found for oid: " + command.userEntraObjectId()));

        log.info("Deleting external user",
                kv("actorEntraOid", actor.entraOid()),
                kv("entraUserId", entraUser.getId()),
                kv("reason", reason.getCode()));

        EntraUserDto userDto = modelMapper.map(entraUser, EntraUserDto.class);
        deleteUserTechServicesService.deactivateUserAndDeleteRoleAssignment(userDto);

        UserAccountStatusAudit audit =
                deleteUserMapper.toAuditEntity(entraUser, reason, String.valueOf(actor.id()));
        auditRepository.save(audit);

        removeUserProfiles(entraUser);
        removeEntraUser(entraUser);

        log.info("User account deleted",
                kv("entraOid", command.userEntraObjectId()),
                kv("actorEntraOid", actor.entraOid()),
                kv("reason", command.deleteReason()));
        return deleteUserMapper.toCommandResult(entraUser);
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
                                Collectors.mapping(AppRole::getRoleIdentifier, Collectors.toSet()),
                                HashSet::new
                        )));
    }
}
