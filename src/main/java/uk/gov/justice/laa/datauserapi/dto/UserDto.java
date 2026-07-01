package uk.gov.justice.laa.datauserapi.dto;

import java.time.LocalDateTime;

public record UserDto(
        String id,
        String entraOid,
        String email,
        String firstName,
        String lastName,
        String status,
        String firmName,
        boolean multiFirmUser,
        LocalDateTime lastLogin
) {}
