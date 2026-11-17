package uk.gov.justice.laa.datauserapi.application.command.service;

import org.junit.jupiter.api.Test;
import org.springframework.aop.support.AopUtils;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.core.MethodParameter;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.bind.support.WebDataBinderFactory;
import uk.gov.justice.laa.datauserapi.application.command.controller.FirmCommandController;
import uk.gov.justice.laa.datauserapi.application.command.handler.BulkDeactivateFirmUsersHandler;
import uk.gov.justice.laa.datauserapi.application.command.mapper.BulkDeactivateFirmUsersMapper;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.EntraUserCommandRepository;
import uk.gov.justice.laa.datauserapi.application.command.shared.repository.FirmCommandRepository;
import uk.gov.justice.laa.datauserapi.config.AsyncConfig;
import uk.gov.justice.laa.datauserapi.config.BulkDeactivationConfig;
import uk.gov.justice.laa.datauserapi.contracts.response.CommandResult;
import uk.gov.justice.laa.datauserapi.dto.AuthenticatedEntraUser;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BulkDeactivationAsyncTest {

    @Configuration
    @Import({AsyncConfig.class, BulkDeactivationConfig.class, BulkDeactivateFirmUsersService.class,
        BulkDeactivateFirmUsersHandler.class, BulkDeactivateFirmUsersMapper.class, FirmCommandController.class})
    static class Config {
    }

    @Test
    void submitsWithoutBlockingAndMapsRealExecutorSaturationToServiceUnavailable() throws Exception {
        FirmCommandRepository firms = mock(FirmCommandRepository.class);
        EntraUserCommandRepository users = mock(EntraUserCommandRepository.class);
        DeactivateUserService worker = mock(DeactivateUserService.class);
        when(firms.existsByCode("FIRM1")).thenReturn(true);
        when(users.findUserEntraOidsByFirmCode("FIRM1")).thenReturn(List.of(UUID.randomUUID().toString()));
        CountDownLatch workersStarted = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        CountDownLatch completed = new CountDownLatch(22);
        ConcurrentLinkedQueue<String> workerThreads = new ConcurrentLinkedQueue<>();
        when(worker.deactivate(any(), any(), any())).thenAnswer(invocation -> {
            workerThreads.add(Thread.currentThread().getName());
            workersStarted.countDown();
            if (!release.await(30, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test workers were not released");
            }
            completed.countDown();
            return CommandResult.success("done");
        });

        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.registerBean(FirmCommandRepository.class, () -> firms);
            context.registerBean(EntraUserCommandRepository.class, () -> users);
            context.registerBean(DeactivateUserService.class, () -> worker);
            context.register(Config.class);
            context.refresh();
            assertThat(AopUtils.isAopProxy(context.getBean(BulkDeactivateFirmUsersService.class))).isTrue();
            ThreadPoolTaskExecutor executor = context.getBean("bulkDeactivationExecutor", ThreadPoolTaskExecutor.class);
            MockMvc mvc = MockMvcBuilders.standaloneSetup(context.getBean(FirmCommandController.class))
                    .setControllerAdvice(new GlobalExceptionHandler())
                    .setCustomArgumentResolvers(actorResolver())
                    .build();
            try {
                assertTimeoutPreemptively(Duration.ofSeconds(10), () -> {
                    // The configured executor accepts two running tasks and twenty queued tasks.
                    for (int i = 0; i < 22; i++) {
                        mvc.perform(submission()).andExpect(status().isAccepted())
                                .andExpect(jsonPath("$.success").value(true));
                    }
                    assertThat(workersStarted.await(5, TimeUnit.SECONDS)).isTrue();
                    assertThat(executor.getActiveCount()).isEqualTo(2);
                    assertThat(executor.getThreadPoolExecutor().getQueue()).hasSize(20);
                    assertThat(release.getCount()).isEqualTo(1);
                    mvc.perform(submission()).andExpect(status().isServiceUnavailable())
                            .andExpect(jsonPath("$.status").value(503))
                            .andExpect(jsonPath("$.detail").value(
                                    "The service is busy processing other requests. Please retry later."));
                });
            } finally {
                release.countDown();
            }
            assertThat(completed.await(10, TimeUnit.SECONDS)).isTrue();
            assertThat(workerThreads).hasSize(22).allMatch(name -> name.startsWith("bulk-deactivation-"));
        }
    }

    private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder submission() {
        return post("/api/v1/commands/firms/bulk-deactivate-users")
                .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                .content("{\"firmId\":\"FIRM1\",\"deactivateReason\":\"FirmClosureorMerger\"}");
    }

    private HandlerMethodArgumentResolver actorResolver() {
        return new HandlerMethodArgumentResolver() {
            @Override
            public boolean supportsParameter(MethodParameter parameter) {
                return parameter.getParameterType().equals(AuthenticatedEntraUser.class);
            }

            @Override
            public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
                                          NativeWebRequest request, WebDataBinderFactory binderFactory) {
                return AuthenticatedEntraUser.builder().entraOid("actor-oid").build();
            }
        };
    }
}
