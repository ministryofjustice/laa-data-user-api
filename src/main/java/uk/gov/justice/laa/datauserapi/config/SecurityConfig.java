package uk.gov.justice.laa.datauserapi.config;

import java.net.URI;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
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

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletResponse;
import uk.gov.justice.laa.datauserapi.contracts.response.ProblemDetail;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;
import uk.gov.justice.laa.datauserapi.exception.ForbiddenResponseConstants;

/**
 * Security configuration for laa-data-user-api.
 * Enforces stateless JWT Bearer authentication on all API endpoints.
 * Requires SCOPE_user_data.read, SCOPE_user_data.admin, or an endpoint-specific admin authority from JWT.
 * Actor identity is derived from the validated JWT {@code oid} claim — no Graph lookup.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtToAuthenticatedEntraUserConverter jwtToAuthenticatedEntraUserConverter;
    private final ObjectMapper objectMapper;

    public SecurityConfig(
            JwtToAuthenticatedEntraUserConverter jwtToAuthenticatedEntraUserConverter,
            ObjectMapper objectMapper) {
        this.jwtToAuthenticatedEntraUserConverter = jwtToAuthenticatedEntraUserConverter;
        this.objectMapper = objectMapper;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session
                .sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )
            .authorizeHttpRequests(authorise -> authorise
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
                .requestMatchers("/api/v1/commands/user-profiles/refresh-silas-status")
                    .hasAuthority("SCOPE_system.admin")
                .requestMatchers("/api/v1/commands/**")
                    .hasAuthority("SCOPE_user_data.admin")
                .requestMatchers("/api/v1/queries/firms/**")
                    .hasAnyAuthority("SCOPE_user_data.read", "SCOPE_user_data.admin")
                .requestMatchers("/api/v1/queries/**")
                    .hasAnyAuthority("SCOPE_user_data.read", "SCOPE_user_data.admin")
                .requestMatchers("/api/v1/**")
                    .hasAnyAuthority("SCOPE_user_data.read", "SCOPE_user_data.admin")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtToAuthenticatedEntraUserConverter))
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler())
            );
        return http.build();
    }

    @Bean
    public AuthenticationEntryPoint authenticationEntryPoint() {
        return (request, response, authException) -> {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
            response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");

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

            response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);

            String requestUri = request.getRequestURI();
            String originalUri = (String) request.getAttribute(RequestDispatcher.FORWARD_REQUEST_URI);
            if (originalUri != null) {
                requestUri = originalUri;
            }

            ProblemDetail problemDetail = new ProblemDetail(
                    ForbiddenResponseConstants.TYPE,
                    ForbiddenResponseConstants.TITLE,
                    HttpStatus.FORBIDDEN.value(),
                    ForbiddenResponseConstants.DETAIL,
                    URI.create(requestUri),
                    null
            );

            response.getWriter().write(objectMapper.writeValueAsString(problemDetail));
        };
    }

}
