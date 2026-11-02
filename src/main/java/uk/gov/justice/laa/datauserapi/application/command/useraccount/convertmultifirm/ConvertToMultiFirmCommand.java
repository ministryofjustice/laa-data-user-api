package uk.gov.justice.laa.datauserapi.application.command.useraccount.convertmultifirm;

import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.util.UUID;

@Builder
public record ConvertToMultiFirmCommand(@NotNull UUID userEntraObjectId) {}
