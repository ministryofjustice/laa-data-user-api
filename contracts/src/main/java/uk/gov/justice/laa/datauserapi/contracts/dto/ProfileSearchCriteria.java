package uk.gov.justice.laa.datauserapi.contracts.dto;

import java.util.List;
import java.util.UUID;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.BindParam;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserProfileSilasStatus;

public record ProfileSearchCriteria(
        @BindParam("page_number") @Min(0) Integer pageNumber,
        @BindParam("page_size") @Min(1) @Max(100) Integer pageSize,
        @BindParam("sort_by") @Size(max = 20) List<String> sortBy,
        String user, String firm,
        @BindParam("firm_id") UUID firmId,
        @BindParam("user_type") UserType userType,
        @BindParam("user_profile_status") UserProfileSilasStatus status,
        @BindParam("app_id") UUID appId,
        @BindParam("app_role_id") UUID appRoleId,
        @BindParam("office_id") String officeId,
        @BindParam("multi_firm_users_filter") Boolean multiFirm,
        @BindParam("firm_admin_filter") Boolean firmAdmin) {
    public ProfileSearchCriteria {
        sortBy = sortBy == null ? null : List.copyOf(sortBy);
    }

    public ProfileSearchCriteria withSortBy(List<String> sorts) {
        return new ProfileSearchCriteria(pageNumber, pageSize, sorts, user, firm, firmId, userType,
                status, appId, appRoleId, officeId, multiFirm, firmAdmin);
    }
}
