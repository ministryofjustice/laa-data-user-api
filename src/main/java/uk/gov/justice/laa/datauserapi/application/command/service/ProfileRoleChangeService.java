package uk.gov.justice.laa.datauserapi.application.command.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.ProfileRoleChangeRepository;
import uk.gov.justice.laa.datauserapi.application.command.userprofile.ProfileRoleChangeCommand;
import uk.gov.justice.laa.datauserapi.contracts.response.UpdateAppRolesResponse;
import uk.gov.justice.laa.datauserapi.exception.ExceptionLogFields;
import static net.logstash.logback.argument.StructuredArguments.kv;

@Service
@Slf4j
public class ProfileRoleChangeService {
    private final ProfileRoleChangePersistenceService persistence;
    private final ProfileRoleChangeRepository repository;
    private final RoleChangeNotificationService notifications;

    public ProfileRoleChangeService(ProfileRoleChangePersistenceService persistence,
                                    ProfileRoleChangeRepository repository,
                                    RoleChangeNotificationService notifications) {
        this.persistence = persistence;
        this.repository = repository;
        this.notifications = notifications;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public UpdateAppRolesResponse change(ProfileRoleChangeCommand command) {
        var result = persistence.change(command);
        if (result.roleChanges().isEmpty()) {
            return result.response();
        }

        boolean successful = true;
        for (var roleChange : result.roleChanges()) {
            try {
                successful &= notifications.sendMessage(result.profile(), roleChange.applicationOid(),
                        roleChange.newRoles(), roleChange.oldRoles());
            } catch (RuntimeException exception) {
                successful = false;
                log.error("CCMS role change notification failed after profile update",
                        ExceptionLogFields.exceptionGroup(exception), ExceptionLogFields.exceptionReason(exception),
                        kv("event", "role_change_notification_failed"),
                        kv("userProfileId", result.profile().profileId()),
                        kv("applicationEntraOid", roleChange.applicationOid()), exception);
            }
        }
        try {
            repository.updateCcmsSyncSuccessful(result.profile().profileId(), successful);
        } catch (DataAccessException exception) {
            log.error("Failed to update CCMS role sync status",
                    ExceptionLogFields.exceptionGroup(exception), ExceptionLogFields.exceptionReason(exception),
                    kv("event", "role_change_sync_status_update_failed"),
                    kv("userProfileId", result.profile().profileId()), exception);
        }
        return result.response();
    }
}
