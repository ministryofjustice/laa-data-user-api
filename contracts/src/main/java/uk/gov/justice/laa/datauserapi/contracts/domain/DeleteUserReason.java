package uk.gov.justice.laa.datauserapi.contracts.domain;

public enum DeleteUserReason {
    CyberRisk,
    LeftFirm,
    ProviderDiscretion,
    ContractEnded,
    RoleChangeorNoLongerRequired,
    FirmClosureorMerger,
    ComplianceBreach,
    DuplicateAccount,
    UserRequest,
    SystemIssue,
    ExpiredInvitation,
    NotActiveAfterMaxLifetime,
    NoGroupsDelete
}
