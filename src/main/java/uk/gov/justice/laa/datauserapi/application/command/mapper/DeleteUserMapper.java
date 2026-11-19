package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.request.DeleteUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.DeleteUserReasonEntity;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.time.LocalDateTime;
import java.util.Set;

@Component
public class DeleteUserMapper {

    public DeleteUserCommand toDeleteUserCommand(DeleteUserRequest request) {
        return DeleteUserCommand.builder()
                .userEntraObjectId(request.userEntraObjectId())
                .deleteReason(request.deleteReason())
                .build();
    }

    public UserAccountStatusAudit toAuditEntity(EntraUser user, DeleteUserReasonEntity reason, String actorId) {
        return UserAccountStatusAudit.builder()
                .userAccountAuditStatus(UserAccountAuditStatus.DELETED)
                .entraUserId(user.getId())
                .statusChangedBy(actorId)
                .statusChangedDate(LocalDateTime.now())
                .userEmail(user.getEmail())
                .userName(user.getFirstName() + " " + user.getLastName())
                .deleteUserReasonEntity(reason)
                .deletedUserCreatedDate(user.getCreatedDate())
                .deletedUserEntraOid(user.getEntraOid())
                .deletedUserType(deletedUserType(user))
                .build();
    }

    // Mirrors how query-side visibility classifies an account: an account without profiles is EXTERNAL only if it is
    // multi-firm, otherwise any internal profile makes it INTERNAL.
    private UserType deletedUserType(EntraUser user) {
        Set<UserProfile> profiles = user.getUserProfiles();
        if (profiles == null || profiles.isEmpty()) {
            return user.isMultiFirmUser() ? UserType.EXTERNAL : UserType.INTERNAL;
        }
        boolean hasInternalProfile = profiles.stream().anyMatch(profile -> profile.getUserType() == UserType.INTERNAL);
        return hasInternalProfile ? UserType.INTERNAL : UserType.EXTERNAL;
    }

    public CommandResult toCommandResult(EntraUser user) {
        return CommandResult.success(String.format("User account '%s' deleted successfully", user.getEntraOid()));
    }
}
