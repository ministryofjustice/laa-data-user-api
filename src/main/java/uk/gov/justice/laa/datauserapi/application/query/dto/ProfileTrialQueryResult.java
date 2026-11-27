package uk.gov.justice.laa.datauserapi.application.query.dto;

import uk.gov.justice.laa.datauserapi.contracts.response.AppRoleViewList;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.UserProfileSummaryPage;

public record ProfileTrialQueryResult(UserProfileSummaryPage page, UserProfileDetailView detail, AppRoleViewList roles) {
}
