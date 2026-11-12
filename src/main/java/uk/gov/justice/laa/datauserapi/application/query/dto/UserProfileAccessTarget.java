package uk.gov.justice.laa.datauserapi.application.query.dto;

import java.util.UUID;

import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;

/** Trusted facts about a target user profile that drive visibility decisions. */
public record UserProfileAccessTarget(
        UserType userType,
        UUID firmId
) {}
