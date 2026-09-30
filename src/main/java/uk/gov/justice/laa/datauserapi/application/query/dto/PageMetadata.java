package uk.gov.justice.laa.datauserapi.application.query.dto;

public record PageMetadata(
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages
) {}
