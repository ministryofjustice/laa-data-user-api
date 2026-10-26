package uk.gov.justice.laa.datauserapi.contracts.response;

import com.fasterxml.jackson.annotation.JsonProperty;

public record UserProfileDetailsOfficeView(

        @JsonProperty("officeId")
        String officeId,

        @JsonProperty("firmId")
        String firmId,

        @JsonProperty("postCode")
        String postCode,

        @JsonProperty("addressLine1")
        String addressLine1,

        @JsonProperty("addressLine2")
        String addressLine2,

        @JsonProperty("addressLine3")
        String addressLine3,

        @JsonProperty("city")
        String city

){}
