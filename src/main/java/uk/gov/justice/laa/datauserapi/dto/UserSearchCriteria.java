package uk.gov.justice.laa.datauserapi.dto;

import java.util.List;

public record UserSearchCriteria(
        List<String> ids,
        List<String> oids,
        List<String> emails,
        List<String> profileIds,
        List<String> roleNames,
        List<String> appNames,
        List<String> offices,
        List<String> firms
) {}
