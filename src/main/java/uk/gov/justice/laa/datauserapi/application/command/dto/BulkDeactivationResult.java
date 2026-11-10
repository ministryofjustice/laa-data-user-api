package uk.gov.justice.laa.datauserapi.application.command.dto;

import java.util.List;

/** Internal per-user outcomes; the existing HTTP response still exposes only aggregate counts. */
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
        DEACTIVATED, INVALID_USER_ID, ACCESS_DENIED, USER_NOT_FOUND, DOWNSTREAM_FAILURE, REJECTED, SYSTEM_FAILURE
    }
}
