package uk.gov.justice.laa.datauserapi.application.query.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import uk.gov.justice.laa.datauserapi.application.query.shared.repository.RoleAssignmentRestrictionQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.dto.RoleAssignmentRestrictionView;
import uk.gov.justice.laa.datauserapi.contracts.response.RoleAssignmentRestrictionViewPage;

@ExtendWith(MockitoExtension.class)
class RoleAssignmentRestrictionQueryServiceTest {

    @Mock
    private RoleAssignmentRestrictionQueryRepository repository;

    @Test
    void returnsItemsWithPageMetadataAndPassesTheFilterAndPagingThrough() {
        UUID appId = UUID.randomUUID();
        RoleAssignmentRestrictionView view = new RoleAssignmentRestrictionView(
                "role", "Role", appId.toString(), "client", "App", "assigning", "Assigning");
        Pageable requested = PageRequest.of(1, 2);
        when(repository.findEligibleRestrictions(appId, requested))
                .thenReturn(new PageImpl<>(List.of(view), requested, 3));

        RoleAssignmentRestrictionViewPage page =
                new RoleAssignmentRestrictionQueryService(repository).getRestrictions(appId, 1, 2);

        assertThat(page.items()).containsExactly(view);
        assertThat(page.page()).isEqualTo(new PageMetadata(1, 2, 3, 2));
    }

    @Test
    void pageOffsetBeyondTheIntegerRangeHasNoItemsAndIsNeverRequestedFromTheRepository() {
        when(repository.findEligibleRestrictions(isNull(), org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 20))))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 45));

        RoleAssignmentRestrictionViewPage page = new RoleAssignmentRestrictionQueryService(repository)
                .getRestrictions(null, Integer.MAX_VALUE, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(new PageMetadata(Integer.MAX_VALUE, 20, 45, 3));
        verify(repository).findEligibleRestrictions(isNull(), org.mockito.ArgumentMatchers.eq(PageRequest.of(0, 20)));
        verifyNoMoreInteractions(repository);
    }

    @Test
    void pageBeyondTheEndHasNoItemsButKeepsTheTotals() {
        Pageable requested = PageRequest.of(9, 20);
        when(repository.findEligibleRestrictions(isNull(), org.mockito.ArgumentMatchers.eq(requested)))
                .thenReturn(new PageImpl<>(List.of(), requested, 5));

        RoleAssignmentRestrictionViewPage page =
                new RoleAssignmentRestrictionQueryService(repository).getRestrictions(null, 9, 20);

        assertThat(page.items()).isEmpty();
        assertThat(page.page()).isEqualTo(new PageMetadata(9, 20, 5, 1));
        ArgumentCaptor<Pageable> pageable = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findEligibleRestrictions(isNull(), pageable.capture());
        assertThat(pageable.getValue().getPageNumber()).isEqualTo(9);
    }
}
