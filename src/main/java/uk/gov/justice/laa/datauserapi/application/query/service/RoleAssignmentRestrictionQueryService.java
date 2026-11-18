package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.application.query.shared.repository.RoleAssignmentRestrictionQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.dto.RoleAssignmentRestrictionView;
import uk.gov.justice.laa.datauserapi.contracts.response.RoleAssignmentRestrictionViewPage;

/** Serves a page of the role assignment restrictions of active applications. */
@Service
@Transactional(readOnly = true)
public class RoleAssignmentRestrictionQueryService {

    private final RoleAssignmentRestrictionQueryRepository repository;

    public RoleAssignmentRestrictionQueryService(RoleAssignmentRestrictionQueryRepository repository) {
        this.repository = repository;
    }

    /** {@code appId} filters on the application of the role being assigned; {@code null} applies no filter. */
    public RoleAssignmentRestrictionViewPage getRestrictions(UUID appId, int pageNumber, int pageSize) {
        Page<RoleAssignmentRestrictionView> page =
                repository.findEligibleRestrictions(appId, PageRequest.of(pageNumber, pageSize));
        return new RoleAssignmentRestrictionViewPage(page.getContent(), new PageMetadata(
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages()));
    }
}
