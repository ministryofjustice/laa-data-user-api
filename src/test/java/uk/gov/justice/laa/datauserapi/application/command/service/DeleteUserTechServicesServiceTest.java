package uk.gov.justice.laa.datauserapi.application.command.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import uk.gov.justice.laa.datauserapi.client.ts.TechServicesClient;
import uk.gov.justice.laa.datauserapi.client.ts.response.ChangeAccountReactivationResponse;
import uk.gov.justice.laa.datauserapi.client.ts.response.TechServicesApiResponse;
import uk.gov.justice.laa.datauserapi.dto.EntraUserDto;
import uk.gov.justice.laa.datauserapi.exception.TechServicesClientException;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteUserTechServicesServiceTest {

    @Mock
    private TechServicesClient techServicesClient;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        logger = (Logger) LoggerFactory.getLogger(DeleteUserTechServicesService.class);
        appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void tearDown() {
        logger.detachAppender(appender);
        appender.stop();
    }

    @Test
    void deactivateUserAndDeleteRoleAssignment_continuesWhenRoleAssignmentDeletionThrows() {
        DeleteUserTechServicesService service = new DeleteUserTechServicesService(techServicesClient);
        EntraUserDto user = new EntraUserDto();
        when(techServicesClient.deactivateUser(user, "RoleChangeorNoLongerRequired"))
                .thenReturn(TechServicesApiResponse.success(ChangeAccountReactivationResponse.builder().build()));
        TechServicesClientException failure = new TechServicesClientException("Tech Services unavailable");
        doThrow(failure)
                .when(techServicesClient).deleteRoleAssignment(user);

        service.deactivateUserAndDeleteRoleAssignment(user);

        verify(techServicesClient).deactivateUser(user, "RoleChangeorNoLongerRequired");
        verify(techServicesClient).deleteRoleAssignment(user);
        assertThat(appender.list).singleElement().satisfies(event -> {
            assertThat(event.getArgumentArray()).extracting(Object::toString)
                    .contains("exception_group=tech_services_client",
                            "exception_reason=downstream_failure");
            assertThat(event.getThrowableProxy()).isNotNull();
        });
    }

    @Test
    void unexpectedProgrammingFaultIsNotSuppressed() {
        DeleteUserTechServicesService service = new DeleteUserTechServicesService(techServicesClient);
        EntraUserDto user = new EntraUserDto();
        when(techServicesClient.deactivateUser(user, "RoleChangeorNoLongerRequired"))
                .thenReturn(TechServicesApiResponse.success(ChangeAccountReactivationResponse.builder().build()));
        IllegalStateException failure = new IllegalStateException("unexpected");
        doThrow(failure).when(techServicesClient).deleteRoleAssignment(user);

        assertThatThrownBy(() -> service.deactivateUserAndDeleteRoleAssignment(user)).isSameAs(failure);
    }
}
