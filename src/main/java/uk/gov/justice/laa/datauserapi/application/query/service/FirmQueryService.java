package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.contracts.dto.FirmSearchView;
import uk.gov.justice.laa.datauserapi.contracts.dto.FirmView;
import uk.gov.justice.laa.datauserapi.contracts.dto.OfficeView;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchAccess;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmSearchViewList;
import uk.gov.justice.laa.datauserapi.application.query.dto.FirmViewPage;
import uk.gov.justice.laa.datauserapi.application.query.dto.OfficeViewList;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.FirmQueryRepository;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.OfficeQueryRepository;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class FirmQueryService {

    private final FirmQueryRepository firmQueryRepository;
    private final OfficeQueryRepository officeQueryRepository;
    private final FirmAuthorisationService authorisationService;

    public FirmQueryService(FirmQueryRepository firmQueryRepository,
                             OfficeQueryRepository officeQueryRepository,
                             FirmAuthorisationService authorisationService) {
        this.firmQueryRepository = firmQueryRepository;
        this.officeQueryRepository = officeQueryRepository;
        this.authorisationService = authorisationService;
    }

    public FirmViewPage listFirms(UUID actorEntraUserId, String firmQuery, int pageNumber, int pageSize) {
        authorisationService.requireFirmDirectoryAccess(actorEntraUserId);
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        String term = firmQuery == null ? "" : firmQuery;
        Page<FirmView> page = firmQueryRepository.search(term, pageable);
        PageMetadata pageMetadata = new PageMetadata(
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return new FirmViewPage(page.getContent(), pageMetadata);
    }

    public FirmView getFirmById(UUID actorEntraUserId, String firmId) {
        authorisationService.requireFirmDirectoryAccess(actorEntraUserId);
        return firmQueryRepository.findViewByCode(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found: " + firmId));
    }

    public FirmSearchViewList searchFirms(UUID actorEntraUserId, String query, int limit) {
        FirmSearchAccess searchAccess = authorisationService.resolveSearchAccess(actorEntraUserId);
        String term = query == null ? "" : query;
        List<FirmSearchView> results =
            firmQueryRepository.searchTypeAhead(
                term, searchAccess.allFirms(), searchAccess.firmCode(), PageRequest.of(0, limit));
        return new FirmSearchViewList(results);
    }

    public OfficeViewList getFirmOffices(UUID actorEntraUserId, String firmId) {
        authorisationService.requireFirmDirectoryAccess(actorEntraUserId);
        if (!firmQueryRepository.existsByCode(firmId)) {
            throw new ResourceNotFoundException("Firm not found: " + firmId);
        }
        List<OfficeView> offices = officeQueryRepository.findByFirmCode(firmId);
        return new OfficeViewList(offices);
    }

}
