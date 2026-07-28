package uk.gov.justice.laa.datauserapi.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.justice.laa.datauserapi.config.SecurityConfig;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ExampleController.class)
@Import(SecurityConfig.class)
class ExampleControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void hello_returns200_whenBearerTokenPresent() throws Exception {
        mockMvc.perform(get("/api/v1/hello").with(jwt()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.message").value("Hello from LAA Data User API"));
    }

    @Test
    void hello_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/hello"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsOidFromJwtClaim() throws Exception {
        String oid = "aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee";
        mockMvc.perform(get("/api/v1/me")
                .with(jwt().jwt(j -> j.claim("oid", oid).subject("test-sub"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.oid").value(oid))
            .andExpect(jsonPath("$.sub").value("test-sub"));
    }

    @Test
    void me_returns401_whenNoToken() throws Exception {
        mockMvc.perform(get("/api/v1/me"))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void me_returnsEmptyOid_whenOidClaimMissing() throws Exception {
        mockMvc.perform(get("/api/v1/me")
                .with(jwt().jwt(j -> j.subject("no-oid-user"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.oid").value(""))
            .andExpect(jsonPath("$.sub").value("no-oid-user"));
    }

    @Test
    void actuatorHealth_permitAll_noTokenRequired() throws Exception {
        mockMvc.perform(get("/actuator/health"))
            .andExpect(status().isNotFound());
    }
}
