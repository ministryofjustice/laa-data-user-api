package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

import java.util.Collection;

@Builder
public record UserProfileDetailView(
    @JsonProperty("userProfileId")
    String userProfileId,

    @JsonProperty("activeProfile")
    Boolean activeProfile,

    @JsonProperty("userType")
    String userType,

    @JsonProperty("email")
    String email,

    @JsonProperty("fullName")
    String fullName,

    @JsonProperty("firmName")
    String firmName,

    @JsonProperty("firmId")
    String firmId,

    @JsonProperty("multiFirmUser")
    Boolean multiFirmUser,

    @JsonProperty("accountStatus")
    String accountStatus,

    @JsonProperty("profileStatus")
    String profileStatus,

    @JsonProperty("hasAppRoles")
    Boolean hasAppRoles,

    @JsonProperty("userEntraObjectId")
    String userEntraObjectId,

    @JsonProperty("unrestrictedOfficeAccess")
    Boolean unrestrictedOfficeAccess,

    @JsonProperty("lastSyncSuccessful")
    Boolean lastSyncSuccessful,

    @JsonProperty("roles")
    Collection<UserProfileDetailsAppRoleView> roles,

    @JsonProperty("offices")
    Collection<UserProfileDetailsOfficeView> offices

){}
