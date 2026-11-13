package uk.gov.justice.laa.datauserapi.client.ts.request;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;

public record CreateUserRequest(
        String givenName,
        String surname,
        String mail,
        @JsonProperty("verification_method") String verificationMethod,
        Set<String> groups) {}
