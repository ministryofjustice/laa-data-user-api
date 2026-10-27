package uk.gov.justice.laa.datauserapi.contracts.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;


import java.util.List;
import java.util.UUID;

public record UserSearchCriteria(
        @Min(value = 0, message = "page_number must be >= 0")
        Integer page_number,

        @Min(value = 1, message = "page_size must be >= 1")
        @Max(value = 100, message = "page_size must be <= 100")
        Integer page_size,

        List<String> sort_by,
        String user,
        String firm,
        UUID firm_id,
        UUID app_id,
        String app_entra_client_id,
        UUID app_role_id,
        UserType user_type,
        UserAccountStatus user_account_status,
        Boolean never_activated_filter,
        Boolean actorInternal,
        Boolean actorExternal,
        UUID actorFirmId,
        Boolean canViewExternalUsers
) {}
