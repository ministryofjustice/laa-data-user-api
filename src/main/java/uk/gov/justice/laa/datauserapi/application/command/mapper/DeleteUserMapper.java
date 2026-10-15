package uk.gov.justice.laa.datauserapi.application.command.mapper;

import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.DeleteUserReasonRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeleteUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountAuditStatus;
import uk.gov.justice.laa.datauserapi.contracts.request.DeleteUserRequest;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.entity.DeleteUserReasonEntity;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.time.LocalDateTime;

@Component
public class DeleteUserMapper {

    private final DeleteUserReasonRepository deleteUserReasonRepository;

    public DeleteUserMapper(DeleteUserReasonRepository deleteUserReasonRepository) {
        this.deleteUserReasonRepository = deleteUserReasonRepository;
    }

    public DeleteUserCommand toDeleteUserCommand(DeleteUserRequest request) {
        return DeleteUserCommand.builder()
                .userEntraObjectId(request.userEntraObjectId())
                .deleteReason(request.deleteReason())
                .build();
    }

    public UserAccountStatusAudit toAuditEntity(EntraUser user, DeleteUserCommand command, String actorId) {
        DeleteUserReasonEntity reason = deleteUserReasonRepository.findByCode(command.deleteReason().name())
                .orElseThrow(() -> new IllegalArgumentException("Delete reason not found: " + command.deleteReason().name()));
        return UserAccountStatusAudit.builder()
                .userAccountAuditStatus(UserAccountAuditStatus.DELETED)
                .entraUserId(user.getId())
                .statusChangedBy(actorId)
                .statusChangedDate(LocalDateTime.now())
                .userEmail(user.getEmail())
                .userName(user.getFirstName() + " " + user.getLastName())
                .deleteUserReasonEntity(reason)
                .deletedUserCreatedDate(user.getCreatedDate())
                .build();
    }

    public CommandResult toCommandResult(EntraUser user) {
        return CommandResult.success(String.format("User account '%s' deleted successfully", user.getEntraOid()));
    }
}
