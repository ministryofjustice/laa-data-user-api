package uk.gov.justice.laa.datauserapi.application.command.mapper;

import jakarta.validation.Valid;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.DeactivateUserReasonRepository;
import uk.gov.justice.laa.datauserapi.application.command.useraccount.DeactivateUserCommand;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.contracts.request.DeactivateUserRequest;
import uk.gov.justice.laa.datauserapi.entity.DeactivateUserReasonLookup;
import uk.gov.justice.laa.datauserapi.entity.EntraUser;
import uk.gov.justice.laa.datauserapi.entity.UserAccountStatusAudit;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
public class DeactivateUserMapper {

    private final DeactivateUserReasonRepository deactivateUserReasonRepository;

    public DeactivateUserMapper(DeactivateUserReasonRepository deactivateUserReasonRepository) {
        this.deactivateUserReasonRepository = deactivateUserReasonRepository;
    }

    public UserAccountStatusAudit toAuditEntity(EntraUser user, DeactivateUserCommand command, String actorId) {
        DeactivateUserReasonLookup deactivateUserReasonLookup = deactivateUserReasonRepository.findByName(command.deactivateReason().name())
                .orElseThrow(() -> new IllegalArgumentException("Deactivate reason not found: " + command.deactivateReason().name()));
        return UserAccountStatusAudit.builder()
                .entraUser(user)
                .userAccountStatus(UserAccountStatus.DEACTIVATED)
                .statusChangedBy(actorId)
                .statusChangedDate(LocalDateTime.now())
                .deactivateUserReasonLookup(deactivateUserReasonLookup)
                .build();
    }

    public CommandResult toCommandResult(EntraUser user) {
        return CommandResult.success(
                String.format("User account '%s' deactivated successfully", user.getEntraOid())
        );
    }

    public DeactivateUserCommand toDeactivateUserCommand(@Valid DeactivateUserRequest deactivateUserRequest) {
        DeactivateUserReason deactivateReason = mapDeactivateReason(deactivateUserRequest.deactivateReason())
                .orElseThrow(() -> new IllegalArgumentException("Invalid deactivate reason: " + deactivateUserRequest.deactivateReason()));
        return DeactivateUserCommand.builder()
                .userEntraObjectId(deactivateUserRequest.userEntraObjectId())
                .deactivateReason(deactivateReason)
                .build();
    }

    private Optional<DeactivateUserReason> mapDeactivateReason(String deactivateReason) {
        try {
            return Optional.of(DeactivateUserReason.valueOf(deactivateReason));
        } catch (IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
