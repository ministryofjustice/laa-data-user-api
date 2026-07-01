package uk.gov.justice.laa.datauserapi.dto;

import uk.gov.justice.laa.datauserapi.entity.UserProfileStatus;
import uk.gov.justice.laa.datauserapi.entity.UserProfileSilasStatus;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

public record UserDetailDto(
        UUID id,
        String email,
        String entraOid,
        boolean multiFirmUser,
        UserProfileStatus status,
        UserProfileSilasStatus silasStatus,
        String firstName,
        String lastName,
        String firmName,
        List<String> roleNames,
        List<String> officeCodes
) implements Serializable {}
