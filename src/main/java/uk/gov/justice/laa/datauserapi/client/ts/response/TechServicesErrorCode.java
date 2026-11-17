package uk.gov.justice.laa.datauserapi.client.ts.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public record TechServicesErrorCode(String value) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public TechServicesErrorCode {}

    @JsonValue
    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return value();
    }
}
