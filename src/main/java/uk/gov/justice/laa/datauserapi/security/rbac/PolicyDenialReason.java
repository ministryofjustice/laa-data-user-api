package uk.gov.justice.laa.datauserapi.security.rbac;

import lombok.Getter;

/** Stable, measurable policy outcomes; descriptions are for human-readable logs. */
@Getter
public enum PolicyDenialReason {
    POLICY_DENIED("policy denied"),
    ACTOR_NOT_ACTIVE_INTERNAL_USER("actor must have an active internal profile"),
    SELF_TARGET_NOT_ALLOWED("actor cannot act on their own account"),
    PROVIDER_ADMIN_NO_ACTIVE_FIRM("provider admin has no active firm"),
    TARGET_USER_INTERNAL("target user is an internal user"),
    TARGET_PROFILE_INTERNAL("target profile belongs to an internal user"),
    TARGET_USER_NOT_DEACTIVATED("target user is not deactivated"),
    REACTIVATION_NOT_ALLOWED_FOR_DEACTIVATION_TYPE("actor roles cannot reactivate this deactivation type"),
    PROVIDER_ADMIN_MULTI_FIRM_TARGET("provider admin cannot act on multi-firm user"),
    TARGET_USER_OUTSIDE_ACTOR_FIRM("target user is not in provider admin's firm"),
    TARGET_PROFILE_OUTSIDE_ACTOR_FIRM("target profile is not in provider admin's firm"),
    TARGET_FIRM_OUTSIDE_ACTOR_FIRM("target firm is not the provider admin's firm"),
    ACTOR_NO_ACTIVE_PROFILE("actor has no active profile with a determinable user type"),
    TARGET_PROFILE_NOT_FOUND("target profile does not exist", true),
    TARGET_PROFILE_NOT_VISIBLE("actor cannot view the target profile", true);

    private final String description;
    /** Denials that must be indistinguishable from a missing resource are answered with a generic 404. */
    private final boolean hidesResourceExistence;

    PolicyDenialReason(String description) {
        this(description, false);
    }

    PolicyDenialReason(String description, boolean hidesResourceExistence) {
        this.description = description;
        this.hidesResourceExistence = hidesResourceExistence;
    }
}
