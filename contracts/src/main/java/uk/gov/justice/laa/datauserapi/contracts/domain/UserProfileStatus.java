package uk.gov.justice.laa.datauserapi.contracts.domain;

import lombok.Getter;

@Getter
public enum UserProfileStatus {
    COMPLETE("COMPLETE"),
    PENDING("PENDING");

    private final String value;

    UserProfileStatus(String value) {
        this.value = value;
    }

}
