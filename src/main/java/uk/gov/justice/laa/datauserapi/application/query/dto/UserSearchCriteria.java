package uk.gov.justice.laa.datauserapi.application.query.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.model.UserType;

import java.util.List;
import java.util.UUID;

public record UserSearchCriteria(
        @Min(value = 0, message = "page_number must be >= 0")
        Integer pageNumber,

        @Min(value = 1, message = "page_size must be >= 1")
        @Max(value = 100, message = "page_size must be <= 100")
        Integer pageSize,

        List<String> sortBy,
        String user,
        String firm,
        UUID firmId,
        UUID appId,
        UUID appRoleId,
        UserType userType,
        UserAccountStatus userAccountStatus,
        Boolean neverActivatedFilter,
        Boolean actorInternal,
        Boolean actorExternal,
        UUID actorFirmId,
        Boolean canViewExternalUsers
) {
}
