package uk.gov.justice.laa.datauserapi.contracts.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record GetUserProfilesRequest (
    @JsonProperty("page_number")
    Integer pageNumber,

    @JsonProperty("page_size")
    Integer pageSize,

    @JsonProperty("sort_by")
    String[] sortBy,

    @JsonProperty("user_type")
    String userType,

    @JsonProperty("user_profile_status")
    String userProfileStatus,

    @JsonProperty("app_id")
    String appId,

    @JsonProperty("app_role_id")
    String appRoleId,

    @JsonProperty("office_id")
    String officeId,

    @JsonProperty("firm_id")
    String firmId,

    @JsonProperty("user")
    String user,

    @JsonProperty("firm")
    String firm,

    @JsonProperty("firm_admin_filter")
    Boolean firmAdmin,

    @JsonProperty("mult_firm_users_filter")
    Boolean multiFirmUser

){}
