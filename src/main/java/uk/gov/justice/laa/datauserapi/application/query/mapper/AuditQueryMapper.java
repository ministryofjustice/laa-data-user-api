package uk.gov.justice.laa.datauserapi.application.query.mapper;

import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditStatusHistoryRow;
import uk.gov.justice.laa.datauserapi.application.query.dto.DeletedUserAuditRow;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeactivateUserReason;
import uk.gov.justice.laa.datauserapi.contracts.domain.DeleteUserReason;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.AccountStatusHistoryView;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditEntraUserView;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditPage;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

@Component
public class AuditQueryMapper {

    /** {@code profileCount} reflects only the profiles the caller may see. */
    public AuditUserDetailView toDetailView(EntraUserDto user, List<UserProfileDetailView> visibleProfiles,
                                            List<AuditStatusHistoryRow> history) {
        AuditEntraUserView entraUser = new AuditEntraUserView(
                user.getEntraOid(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getUserAccountStatus(),
                user.isMultiFirmUser(),
                user.isMailOnly(),
                user.getLastSyncedOn(),
                user.getCreatedDate(),
                user.getLastModified(),
                visibleProfiles.size(),
                user.getInvitationStatus() == null ? null : user.getInvitationStatus().toString(),
                null);
        return new AuditUserDetailView(entraUser, visibleProfiles, history.stream().map(this::toHistoryView).toList());
    }

    public DeletedUserAuditPage toDeletedPage(Page<DeletedUserAuditRow> page) {
        return new DeletedUserAuditPage(
                page.getContent().stream().map(this::toDeletedView).toList(),
                new PageMetadata(page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }

    private AccountStatusHistoryView toHistoryView(AuditStatusHistoryRow row) {
        return new AccountStatusHistoryView(
                row.userEntraObjectId(),
                row.statusChange(),
                row.statusChangedDate(),
                row.statusChangedBy(),
                enumByName(DeactivateUserReason.values(), row.deactivateReasonName()),
                row.deactivateReasonLabel());
    }

    private DeletedUserAuditView toDeletedView(DeletedUserAuditRow row) {
        return new DeletedUserAuditView(
                row.userEntraObjectId(),
                row.name(),
                row.email(),
                row.deletedBy(),
                row.deletedDate(),
                enumByName(DeleteUserReason.values(), row.deleteReasonCode()),
                row.deleteReasonLabel());
    }

    private static <E extends Enum<E>> E enumByName(E[] values, String name) {
        return Optional.ofNullable(name)
                .flatMap(value -> Arrays.stream(values).filter(candidate -> candidate.name().equals(value)).findFirst())
                .orElse(null);
    }
}
