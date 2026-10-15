package uk.gov.justice.laa.datauserapi.contracts.domain;

public enum UserAccountStatus {
    ACTIVE("Active"),
    DEACTIVATED("Deactivated"),
    ACTIVATION_REQUIRED("Activation Required"),
    UNKNOWN("Unknown");

    private final String value;

    UserAccountStatus(String value) {
        this.value = value;
    }
}
