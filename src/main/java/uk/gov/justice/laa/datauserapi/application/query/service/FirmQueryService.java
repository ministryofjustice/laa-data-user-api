package uk.gov.justice.laa.datauserapi.application.query.service;

import java.util.List;
import java.util.Map;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import uk.gov.justice.laa.datauserapi.contracts.domain.FirmType;
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
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.ResourceNotFoundException;

@Service
@Transactional(readOnly = true)
public class FirmQueryService {

    private static final Map<String, String> FIRM_SORT_PROPERTIES = Map.of(
            "code", "code",
            "name", "name",
            "type", "type");

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

    public FirmViewPage listFirms(
            AuthenticatedEntraUser authenticatedUser,
            String firmQuery,
            FirmType firmType,
            int pageNumber,
            int pageSize,
            String sortBy) {
        authorisationService.requireFirmDirectoryAccess(authenticatedUser);
        Pageable pageable = PageRequest.of(pageNumber, pageSize, toSort(sortBy));
        String term = firmQuery == null ? "" : firmQuery;
        Page<FirmView> page = firmQueryRepository.search(term, firmType, pageable);
        PageMetadata pageMetadata = new PageMetadata(
                page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
        return new FirmViewPage(page.getContent(), pageMetadata);
    }

    private Sort toSort(String sortValue) {
        String value = sortValue == null || sortValue.isBlank() ? "name,asc" : sortValue;
        String[] parts = value.split(",", -1);
        String property = parts.length > 0 ? FIRM_SORT_PROPERTIES.get(parts[0]) : null;
        if (property == null || parts.length > 2 || (parts.length == 2 && parts[1].isBlank())) {
            throw new IllegalArgumentException("Unsupported firm sort value: " + value);
        }

        Sort.Direction direction = parts.length == 2
                ? Sort.Direction.fromString(parts[1])
                : Sort.Direction.ASC;
        Sort.Order order = new Sort.Order(direction, property);
        if (property.equals("name")) {
            order = order.ignoreCase();
        }
        Sort sort = Sort.by(order);
        return property.equals("code") ? sort : sort.and(Sort.by(new Sort.Order(direction, "code")));
    }

    public FirmView getFirmById(AuthenticatedEntraUser authenticatedUser, String firmId) {
        authorisationService.requireFirmDirectoryAccess(authenticatedUser);
        return firmQueryRepository.findViewByCode(firmId)
                .orElseThrow(() -> new ResourceNotFoundException("Firm not found: " + firmId));
    }

    public FirmSearchViewList searchFirms(AuthenticatedEntraUser authenticatedUser, String query, int limit) {
        FirmSearchAccess searchAccess = authorisationService.resolveSearchAccess(authenticatedUser);
        String term = query == null ? "" : query;
        List<FirmSearchView> results =
            firmQueryRepository.searchTypeAhead(
                term, searchAccess.allFirms(), searchAccess.firmCode(), PageRequest.of(0, limit));
        return new FirmSearchViewList(results);
    }

    public OfficeViewList getFirmOffices(AuthenticatedEntraUser authenticatedUser, String firmId) {
        authorisationService.requireFirmDirectoryAccess(authenticatedUser);
        if (!firmQueryRepository.existsByCode(firmId)) {
            throw new ResourceNotFoundException("Firm not found: " + firmId);
        }
        List<OfficeView> offices = officeQueryRepository.findByFirmCode(firmId);
        return new OfficeViewList(offices);
    }

}
