package uk.gov.justice.laa.datauserapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import uk.gov.justice.laa.datauserapi.contracts.response.ProblemDetail;

import java.io.IOException;
import java.net.URI;

/**
 * Security configuration for laa-data-user-api.
 * Enforces stateless JWT Bearer authentication on all API endpoints.
 * Actor identity is derived from the validated JWT {@code oid} claim — no Graph lookup.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectMapper mapper) throws Exception {
        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            response.setHeader("WWW-Authenticate", "Bearer");
            writeProblem(mapper, request, response, 401);
        };
        AccessDeniedHandler forbidden = (request, response, exception) -> writeProblem(mapper, request, response, 403);
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/health/readiness", "/actuator/health/liveness", "/actuator/info").permitAll()
                .requestMatchers("/actuator/prometheus")
                .access((auth, context) -> {
                    boolean allowed =
                        new IpAddressMatcher("127.0.0.1").matches(context.getRequest())
                            || new IpAddressMatcher("::1").matches(context.getRequest())
                            || new IpAddressMatcher("10.0.0.0/8").matches(context.getRequest())
                            || new IpAddressMatcher("172.16.0.0/12").matches(context.getRequest())
                            || new IpAddressMatcher("192.168.0.0/16").matches(context.getRequest());
                    return new AuthorizationDecision(allowed);
                })
                .requestMatchers("/api/v1/commands/users/create").hasAuthority("SCOPE_user_data.admin")
                .requestMatchers("/api/v1/**").hasAuthority("SCOPE_user_data.read")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(unauthorized)
                .accessDeniedHandler(forbidden))
            .oauth2ResourceServer(oauth2 -> oauth2
                .authenticationEntryPoint(unauthorized)
                .accessDeniedHandler(forbidden)
                .jwt(org.springframework.security.config.Customizer.withDefaults())
            );
        return http.build();
    }

    private void writeProblem(ObjectMapper mapper, HttpServletRequest request,
                              HttpServletResponse response, int status) throws IOException {
        response.setStatus(status);
        response.setContentType("application/problem+json");
        var problem = new ProblemDetail(
                URI.create("about:blank"), status == 401 ? "Unauthorized" : "Forbidden", status,
                "Request is not authorised", URI.create(request.getRequestURI()), null);
        mapper.writeValue(response.getOutputStream(), problem);
    }
}
