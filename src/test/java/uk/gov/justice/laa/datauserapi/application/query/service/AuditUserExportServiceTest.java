package uk.gov.justice.laa.datauserapi.application.query.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import uk.gov.justice.laa.datauserapi.application.query.dto.AuditUserExportRow;
import uk.gov.justice.laa.datauserapi.application.query.mapper.AuditUserExportMapper;
import uk.gov.justice.laa.datauserapi.application.query.queryauditusersexport.ExportAuditUsersQuery;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuditUserExportQueryRepository;
import uk.gov.justice.laa.datauserapi.contracts.domain.UserType;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditUserExportCriteria;
import uk.gov.justice.laa.datauserapi.entity.UserProfile;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditUserExportServiceTest {

    @Mock
    private AuditUserExportQueryRepository repository;

    @Mock
    private AuditUserExportMapper mapper;

    private AuditUserExportService service;

    private static final AuditUserExportCriteria CRITERIA =
            new AuditUserExportCriteria(UserType.INTERNAL, null, null, null, null, null, null);

    @org.junit.jupiter.api.BeforeEach
    void setUp() {
        service = new AuditUserExportService(repository, mapper);
    }

    @Test
    void export_withNoMatches_returnsEmptyListWithoutFetchingDetails() {
        when(repository.findMatchingProfileIds(eq(CRITERIA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 500), 0));

        List<AuditUserExportRow> rows = service.export(new ExportAuditUsersQuery(CRITERIA));

        assertThat(rows).isEmpty();
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void export_mapsEachMatchingProfileBatch() {
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        List<UUID> ids = List.of(first, second);

        when(repository.findMatchingProfileIds(eq(CRITERIA), any(Pageable.class)))
                .thenReturn(new PageImpl<>(ids, PageRequest.of(0, 500), 2));

        UserProfile firstProfile = mock(UserProfile.class);
        UserProfile secondProfile = mock(UserProfile.class);
        when(repository.findDetailsWithRolesAndOffices(ids))
                .thenReturn(List.of(secondProfile, firstProfile));

        AuditUserExportRow firstRow = mock(AuditUserExportRow.class);
        AuditUserExportRow secondRow = mock(AuditUserExportRow.class);
        when(mapper.toRows(ids, List.of(secondProfile, firstProfile)))
                .thenReturn(List.of(firstRow, secondRow));

        List<AuditUserExportRow> rows = service.export(new ExportAuditUsersQuery(CRITERIA));

        assertThat(rows).containsExactly(firstRow, secondRow);
    }

    @Test
    void export_fetchesEveryPageUntilExhausted() {
        UUID id1 = UUID.randomUUID();
        UUID id2 = UUID.randomUUID();

        Pageable firstPageable = PageRequest.of(0, 500);
        // Total elements exceed the page size so a second page genuinely exists.
        Page<UUID> firstPage = new PageImpl<>(List.of(id1), firstPageable, 501);
        Page<UUID> secondPage = new PageImpl<>(List.of(id2), firstPageable.next(), 501);

        when(repository.findMatchingProfileIds(eq(CRITERIA), eq(firstPageable))).thenReturn(firstPage);
        when(repository.findMatchingProfileIds(eq(CRITERIA), eq(firstPageable.next()))).thenReturn(secondPage);

        UserProfile profile1 = mock(UserProfile.class);
        UserProfile profile2 = mock(UserProfile.class);
        when(repository.findDetailsWithRolesAndOffices(List.of(id1))).thenReturn(List.of(profile1));
        when(repository.findDetailsWithRolesAndOffices(List.of(id2))).thenReturn(List.of(profile2));

        AuditUserExportRow row1 = mock(AuditUserExportRow.class);
        AuditUserExportRow row2 = mock(AuditUserExportRow.class);
        when(mapper.toRows(List.of(id1), List.of(profile1))).thenReturn(List.of(row1));
        when(mapper.toRows(List.of(id2), List.of(profile2))).thenReturn(List.of(row2));

        List<AuditUserExportRow> rows = service.export(new ExportAuditUsersQuery(CRITERIA));

        assertThat(rows).containsExactly(row1, row2);
        verify(repository).findMatchingProfileIds(CRITERIA, firstPageable);
        verify(repository).findMatchingProfileIds(CRITERIA, firstPageable.next());
    }
}
