package uk.gov.justice.laa.datauserapi.contracts.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.BindParam;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserAccountStatus;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;


import java.util.List;
import java.util.UUID;

public record UserSearchCriteria(
        @BindParam("page_number")
        @Min(value = 0, message = "page_number must be >= 0")
        Integer pageNumber,

        @BindParam("page_size")
        @Min(value = 1, message = "page_size must be >= 1")
        @Max(value = 100, message = "page_size must be <= 100")
        Integer pageSize,

        @BindParam("sort_by")
        List<String> sortBy,

        String user,

        @BindParam("email_query")
        String emailQuery,

        String firm,

        @BindParam("firm_id")
        UUID firmId,

        @BindParam("app_id")
        UUID appId,

        @BindParam("app_entra_client_id")
        String appEntraClientId,

        @BindParam("app_role_id")
        UUID appRoleId,

        @BindParam("user_type")
        UserType userType,

        @BindParam("user_account_status")
        UserAccountStatus userAccountStatus,

        @BindParam("never_activated_filter")
        Boolean neverActivatedFilter,

        Boolean actorInternal,
        Boolean actorExternal,
        UUID actorFirmId,
        Boolean canViewExternalUsers
) {}
