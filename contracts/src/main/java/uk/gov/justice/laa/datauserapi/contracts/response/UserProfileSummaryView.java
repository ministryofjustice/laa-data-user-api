package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;

@Builder
public record UserProfileSummaryView(
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
        Boolean hasAppRoles
){}
