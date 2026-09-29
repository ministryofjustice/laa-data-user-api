package uk.gov.justice.laa.datauserapi.model;

import lombok.Getter;

@Getter
public enum AuthzRoleType {
    NONE("None"),
    PROVIDER_ADMIN("Provider Admin"),
    LAA("Legal Aid Agency"),
    PRIVILEGED("Privileged");

    private final String label;

    AuthzRoleType(String label) {
        this.label = label;
    }

}
