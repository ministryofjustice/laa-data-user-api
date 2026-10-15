package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserProfileDetailsAppRoleView(

        @JsonProperty("appRoleId")
        String appRoleId,

        @JsonProperty("appId")
        String appId,

        @JsonProperty("appName")
        String appName,

        @JsonProperty("name")
        String name,

        @JsonProperty("description")
        String description,

        @JsonProperty("authzRole")
        Boolean authzRole,

        @JsonProperty("ccmsCode")
        String ccmsCode,

        @JsonProperty("legacySync")
        Boolean legacySync,

        @JsonProperty("ordinal")
        Integer ordinal,

        @JsonProperty("appRoleUserTypeRestriction")
        String[] appRoleUserTypeRestriction,

        @JsonProperty("firmTypeRestriction")
        String[] firmTypeRestriction

){}
