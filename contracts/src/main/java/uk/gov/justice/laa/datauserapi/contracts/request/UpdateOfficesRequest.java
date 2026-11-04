package uk.gov.justice.laa.datauserapi.contracts.request;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

@JsonTypeInfo(use = JsonTypeInfo.Id.DEDUCTION)
@JsonSubTypes({
        @JsonSubTypes.Type(UpdateUserProfileOfficesRequest.class),
        @JsonSubTypes.Type(UpdateUserProfileOfficesUnrestrictedRequest.class)
})
public sealed interface UpdateOfficesRequest
        permits UpdateUserProfileOfficesRequest, UpdateUserProfileOfficesUnrestrictedRequest {
}
