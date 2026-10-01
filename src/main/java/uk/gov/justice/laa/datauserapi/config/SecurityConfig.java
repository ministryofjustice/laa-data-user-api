package uk.gov.justice.laa.datauserapi.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import uk.gov.justice.laa.datauserapi.contracts.response.ProblemDetail;

import java.net.URI;

/**
 * Security configuration for laa-data-user-api.
 * Enforces stateless JWT Bearer authentication on all API endpoints.
 * Requires SCOPE_user_data.read or SCOPE_user_data.admin authority from JWT.
 * Actor identity is derived from the validated JWT {@code oid} claim — no Graph lookup.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
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
                .requestMatchers("/api/v1/**").hasAnyAuthority("SCOPE_user_data.read", "SCOPE_user_data.admin")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> {})
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            );
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/problem+json");

            String requestUri = request.getRequestURI();
            String originalUri = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
            if (originalUri != null) {
                requestUri = originalUri;
            }

            ProblemDetail problemDetail = new ProblemDetail(
                    URI.create("https://silas.laa.gov.uk/errors/unauthorized"),
                    "Unauthorized",
                    HttpStatus.UNAUTHORIZED.value(),
                    "Malformed or missing token",
                    URI.create(requestUri),
                    null
            );

            response.getWriter().write(new ObjectMapper().writeValueAsString(problemDetail));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType("application/problem+json");

            String requestUri = request.getRequestURI();
            String originalUri = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
            if (originalUri != null) {
                requestUri = originalUri;
            }

            ProblemDetail problemDetail = new ProblemDetail(
                    URI.create("https://silas.laa.gov.uk/errors/forbidden"),
                    "Forbidden",
                    HttpStatus.FORBIDDEN.value(),
                    "Missing required scope: user_data.read or user_data.admin",
                    URI.create(requestUri),
                    null
            );

            response.getWriter().write(new ObjectMapper().writeValueAsString(problemDetail));
        };
    }

}
