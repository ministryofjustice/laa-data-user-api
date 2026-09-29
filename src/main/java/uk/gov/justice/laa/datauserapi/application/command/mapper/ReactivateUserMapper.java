package uk.gov.justice.laa.datauserapi.application.command.mapper;

import jakarta.validation.Valid;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.ReactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.request.ReactivateUserRequest;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.time.LocalDateTime;

@Component
public class ReactivateUserMapper {

    public UserAccountStatusAudit toAuditEntity(EntraUser user, String actorId, String comments) {
        return UserAccountStatusAudit.builder()
                .entraUser(user)
                .userEmail(user.getEmail())
                .userName(user.getFirstName() + " " + user.getLastName())
                .userAccountStatus(UserAccountStatus.ACTIVE)
                .statusChangedDate(LocalDateTime.now())
                .statusChangedBy(actorId)
                .deactivateUserReasonLookup(null)
                .comments(comments)
                .build();
    }

    public CommandResult toCommandResult(EntraUser user) {
        return CommandResult.success(
                String.format("User account '%s' reactivated successfully", user.getEntraOid())
        );
    }

    public ReactivateUserCommand toReactivateUserCommand(@Valid ReactivateUserRequest reactivateUserRequest) {
        return new ReactivateUserCommand(
                reactivateUserRequest.userEntraObjectId(),
                reactivateUserRequest.comments()
        );
    }
}
