package uk.gov.justice.laa.datauserapi.application.query.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import uk.gov.justice.laa.datauserapi.application.query.handler.AuditUserDetailHandler;
import uk.gov.justice.laa.datauserapi.application.query.handler.DeletedUsersAuditHandler;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.AuditUserDetailQuery;
import uk.gov.justice.laa.datauserapi.application.query.queryaudit.DeletedUsersAuditQuery;
import uk.gov.justice.laa.datauserapi.contracts.dto.AuditDeletedUsersCriteria;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.contracts.response.AuditUserDetailView;
import uk.gov.justice.laa.datauserapi.contracts.response.DeletedUserAuditPage;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.InvalidUuidFormatException;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuditQueryControllerTest {

    private static final AuthenticatedEntraUser ACTOR = AuthenticatedEntraUser.builder().entraOid("actor").build();

    @Mock
    private AuditUserDetailHandler detailHandler;
    @Mock
    private DeletedUsersAuditHandler deletedHandler;

    private AuditQueryController controller;

    @BeforeEach
    void setUp() {
        controller = new AuditQueryController(detailHandler, deletedHandler);
    }

    @Test
    void getAuditUserDetailDelegatesToHandler() {
        String oid = UUID.randomUUID().toString();
        AuditUserDetailView view = mock(AuditUserDetailView.class);
        when(detailHandler.handle(new AuditUserDetailQuery(oid), ACTOR)).thenReturn(view);

        ResponseEntity<AuditUserDetailView> response = controller.getAuditUserDetail(oid, ACTOR);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(view);
    }

    @Test
    void getAuditUserDetailRejectsInvalidIdentifierBeforeCallingHandler() {
        assertThatThrownBy(() -> controller.getAuditUserDetail("not-a-uuid", ACTOR))
                .isInstanceOf(InvalidUuidFormatException.class);

        verifyNoInteractions(detailHandler);
    }

    @Test
    void getDeletedUsersReturnsEmptyPageUnchanged() {
        AuditDeletedUsersCriteria criteria = new AuditDeletedUsersCriteria(0, 20, "nobody");
        DeletedUserAuditPage empty = new DeletedUserAuditPage(List.of(), new PageMetadata(0, 20, 0, 0));
        when(deletedHandler.handle(new DeletedUsersAuditQuery(criteria), ACTOR)).thenReturn(empty);

        ResponseEntity<DeletedUserAuditPage> response = controller.getDeletedUsers(criteria, ACTOR);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isSameAs(empty);
        verify(deletedHandler).handle(new DeletedUsersAuditQuery(criteria), ACTOR);
    }
}
