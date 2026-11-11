package uk.gov.justice.laa.datauserapi.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
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

import jakarta.servlet.ServletException;
import org.springframework.web.servlet.HandlerExceptionResolver;
import uk.gov.justice.laa.datauserapi.authorisation.JwtToAuthenticatedEntraUserConverter;

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
    private final HandlerExceptionResolver exceptionResolver;

    public SecurityConfig(JwtToAuthenticatedEntraUserConverter converter,
                          @Qualifier("handlerExceptionResolver") HandlerExceptionResolver exceptionResolver) {
        this.jwtToAuthenticatedEntraUserConverter = converter;
        this.exceptionResolver = exceptionResolver;
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
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(authenticationEntryPoint())
                .accessDeniedHandler(accessDeniedHandler()))
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
            if (exceptionResolver.resolveException(request, response, null, authException) == null) {
                throw new ServletException("Authentication failure could not be resolved", authException);
            }
        };
    }

    @Bean
    public AccessDeniedHandler accessDeniedHandler() {
        return (request, response, accessDeniedException) -> {
            if (exceptionResolver.resolveException(request, response, null, accessDeniedException) == null) {
                throw new ServletException("Access denial could not be resolved", accessDeniedException);
            }
        };
    }

}
