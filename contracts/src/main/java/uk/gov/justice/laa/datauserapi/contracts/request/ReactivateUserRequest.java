package uk.gov.justice.laa.datauserapi.contracts.request;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ReactivateUserRequest(@NotNull UUID userEntraObjectId, String comments) {}
