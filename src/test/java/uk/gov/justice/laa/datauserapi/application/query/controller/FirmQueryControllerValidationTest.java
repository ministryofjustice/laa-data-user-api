package uk.gov.justice.laa.datauserapi.application.query.controller;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.validation.beanvalidation.MethodValidationInterceptor;

import uk.gov.justice.laa.datauserapi.application.query.dto.FirmViewPage;
import uk.gov.justice.laa.datauserapi.application.query.service.FirmQueryService;
import uk.gov.justice.laa.datauserapi.contracts.dto.PageMetadata;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class FirmQueryControllerValidationTest {

    private final FirmQueryService firmQueryService = mock(FirmQueryService.class);
    private ValidatorFactory validatorFactory;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        ProxyFactory proxyFactory = new ProxyFactory(new FirmQueryController(firmQueryService));
        proxyFactory.addAdvice(new MethodValidationInterceptor(validatorFactory.getValidator()));
        mockMvc = standaloneSetup(proxyFactory.getProxy())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @AfterEach
    void tearDown() {
        validatorFactory.close();
    }

    @Test
    void queryFirms_rejectsMalformedSortByValuesWithBadRequest() throws Exception {
        mockMvc.perform(get("/api/v1/queries/firms").param("sort_by", "foo,asc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("https://silas.laa.gov.uk/errors/validation-error"))
                .andExpect(jsonPath("$.title").value("Validation Error"))
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.detail").value("One or more request parameters failed validation."))
                .andExpect(jsonPath("$.instance").value("/api/v1/queries/firms"))
                .andExpect(jsonPath("$.errors").isArray());

        for (String sortBy : List.of("foo", "name,", "name,desc,extra", "name,ascending", "")) {
            mockMvc.perform(get("/api/v1/queries/firms").param("sort_by", sortBy))
                    .andExpect(status().isBadRequest());
        }

        verifyNoInteractions(firmQueryService);
    }

    @Test
    void queryFirms_acceptsSupportedSortValue() throws Exception {
        UUID actorId = UUID.randomUUID();
        FirmViewPage page = new FirmViewPage(List.of(), new PageMetadata(0, 20, 0, 0));
        for (String sortBy : List.of("type,desc", "name,ASC", "type,DESC")) {
            when(firmQueryService.listFirms(eq(actorId), isNull(), eq(0), eq(20), eq(sortBy)))
                    .thenReturn(page);

            AuthenticatedEntraUser authenticatedEntraUser = AuthenticatedEntraUser.builder()
                    .entraOid(actorId.toString())
                    .build();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(authenticatedEntraUser, "token"));
            try {
                int responseStatus = mockMvc.perform(get("/api/v1/queries/firms").param("sort_by", sortBy))
                        .andReturn()
                        .getResponse()
                        .getStatus();

                assertThat(responseStatus).isEqualTo(200);
                verify(firmQueryService).listFirms(actorId, null, 0, 20, sortBy);
            } finally {
                SecurityContextHolder.clearContext();
            }
        }
    }
}
