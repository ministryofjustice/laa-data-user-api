package uk.gov.justice.laa.datauserapi.application.command.dto;

import java.util.List;

/** Internal per-user outcomes; the asynchronous HTTP response does not expose results. */
public record BulkDeactivationResult(List<UserOutcome> outcomes) {

    public BulkDeactivationResult {
        outcomes = List.copyOf(outcomes);
    }

    public long deactivated() {
        return outcomes.stream().filter(outcome -> outcome.status() == Status.DEACTIVATED).count();
    }

    public long failed() {
        return outcomes.size() - deactivated();
    }

    public record UserOutcome(String entraOid, Status status) {
    }

    public enum Status {
        DEACTIVATED, INVALID_USER_ID, ACCESS_DENIED, USER_NOT_FOUND, FIRM_NOT_FOUND,
        DOWNSTREAM_FAILURE, DATABASE_FAILURE, VALIDATION_FAILURE, EXECUTOR_REJECTED, REJECTED, SYSTEM_FAILURE
    }
}
