package uk.gov.justice.laa.datauserapi.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import java.util.ArrayList;
import java.util.Collection;

/**
 * Security configuration for laa-data-user-api.
 * Enforces stateless JWT Bearer authentication on all API endpoints.
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
                .requestMatchers("/api/v1/commands/users/create").hasAuthority("SCOPE_user_data.admin")
                .requestMatchers("/api/v1/**").hasAuthority("SCOPE_user_data.read")
                .anyRequest().authenticated()
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(problemEntryPoint(401, "Unauthorized"))
                .accessDeniedHandler(problemDeniedHandler()))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter()))
            );
        return http.build();
    }

    private AuthenticationEntryPoint problemEntryPoint(int status, String title) {
        return (request, response, exception) -> {
            response.setStatus(status);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"type\":\"https://laa.gov.uk/problems/" + status
                    + "\",\"title\":\"" + title + "\",\"status\":" + status
                    + ",\"detail\":\"Request is not authorised\",\"instance\":\""
                    + request.getRequestURI() + "\"}");
        };
    }

    private AccessDeniedHandler problemDeniedHandler() {
        return (request, response, exception) -> {
            response.setStatus(403);
            response.setContentType("application/problem+json");
            response.getWriter().write("{\"type\":\"https://laa.gov.uk/problems/403\","
                    + "\"title\":\"Forbidden\",\"status\":403,"
                    + "\"detail\":\"Request is not authorised\",\"instance\":\""
                    + request.getRequestURI() + "\"}");
        };
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<org.springframework.security.core.GrantedAuthority> authorities =
                    new ArrayList<>(scopes.convert(jwt));
            Object roles = jwt.getClaims().get("roles");
            if (roles instanceof Collection<?> roleCollection) {
                roleCollection.forEach(role ->
                        authorities.add(new SimpleGrantedAuthority(role.toString())));
            } else if (roles instanceof String role) {
                authorities.add(new SimpleGrantedAuthority(role));
            }
            return authorities;
        });
        return converter;
    }
}
