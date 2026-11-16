package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

import uk.gov.justice.laa.datauserapi.contracts.dto.AppView;

public record AppViewList(
        List<AppView> items
) {}
