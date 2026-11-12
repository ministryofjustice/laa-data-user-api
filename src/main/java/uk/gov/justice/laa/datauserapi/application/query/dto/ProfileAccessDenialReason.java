package uk.gov.justice.laa.datauserapi.application.query.dto;

/** Server-side reason a user profile access query was denied. Never returned to clients. */
public enum ProfileAccessDenialReason {
    PROFILE_NOT_FOUND,
    PROFILE_NOT_VISIBLE
}
