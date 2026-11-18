package uk.gov.justice.laa.datauserapi.application.query.dto;

public record UserSearchAuthorizationContext(
        boolean actorHasActiveProfile,
        boolean actorCanViewExternalUsers,
        String actorUserType
) {}
