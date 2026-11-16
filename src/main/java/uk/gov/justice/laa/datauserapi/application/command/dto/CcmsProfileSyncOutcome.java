package uk.gov.justice.laa.datauserapi.application.command.dto;

public enum CcmsProfileSyncOutcome {
    SUCCESS,
    SKIPPED,
    INVALID_DATA,
    DOWNSTREAM_FAILURE,
    PERSISTENCE_FAILURE,
    UNEXPECTED_FAILURE
}
