package uk.gov.justice.laa.datauserapi.contracts.response;

import java.util.List;

import uk.gov.justice.laa.datauserapi.contracts.dto.AppRoleView;

public record AppRoleViewList(
        List<AppRoleView> items
) {}
