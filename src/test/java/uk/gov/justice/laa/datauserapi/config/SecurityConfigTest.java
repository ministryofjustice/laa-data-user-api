package uk.gov.justice.laa.datauserapi.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.gov.justice.laa.datauserapi.application.query.shared.repository.AuthenticatedEntraUserQueryRepository;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.exception.GlobalExceptionHandler;
import org.springframework.http.MediaType;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

@WebMvcTest(useDefaultFilters = false)
@Import({
    SecurityConfig.class,
    GlobalExceptionHandler.class,
    SecurityConfigTest.JwtDecoderConfiguration.class,
    JwtToAuthenticatedEntraUserConverter.class,
    AuthenticatedEntraUserQueryRepository.class
})
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private AuthenticatedEntraUserQueryRepository authenticatedEntraUserQueryRepository;

    @Test
    void readScopeCanAccessFirmQueryEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/queries/firms/search")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user_data.read"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/queries/firms")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user_data.read"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/queries/firms/123")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user_data.read"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/queries/firms/123/offices")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user_data.read"))))
                .andExpect(status().isOk());
    }

    @Test
    void adminScopeCanAccessFirmQueryEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/queries/firms")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user_data.admin"))))
                .andExpect(status().isOk());
    }

    @Test
    void readScopeCanAccessOtherQueryAndApiEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/queries/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user_data.read"))))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/me")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user_data.read"))))
                .andExpect(status().isOk());
    }

    @Test
    void readScopeCannotAccessCommandEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/commands/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user_data.read"))))
                .andExpect(status().isForbidden())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.status").value(403));
    }

    @Test
    void missingAuthenticationUsesGlobalExceptionHandler() throws Exception {
        mockMvc.perform(get("/api/v1/commands/users"))
                .andExpect(status().isUnauthorized())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(header().string("WWW-Authenticate", "Bearer"))
                .andExpect(jsonPath("$.detail").value("Malformed or missing token"));
    }

    @Test
    void adminScopeCanAccessCommandEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/commands/users")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_user_data.admin"))))
                .andExpect(status().isOk());
    }

    @RestController
    @RequestMapping("/api/v1")
    public static class TestController {

        @GetMapping({
            "/queries/firms/search",
            "/queries/firms",
            "/queries/firms/{firmId}",
            "/queries/firms/{firmId}/offices",
            "/queries/users",
            "/me",
            "/commands/users"
        })
        String endpoint() {
            return "ok";
        }
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtDecoderConfiguration {

        @Bean
        JwtDecoder jwtDecoder() {
            return token -> {
                throw new UnsupportedOperationException("Mock JWT authentication should not invoke the decoder");
            };
        }

        @Bean
        TestController testController() {
            return new TestController();
        }
    }
}
