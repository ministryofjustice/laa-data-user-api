package uk.gov.justice.laa.datauserapi.application.query.dto;

/**
 * Authorization context for search users queries.
 * Holds facts about the actor required to determine search authorization.
 */
public record UserSearchAuthorizationContext(
        boolean actorHasActiveProfile,
        boolean actorCanViewExternalUsers,
        String actorUserType
) {}
