package se.comerit.resurs.security;

import jakarta.servlet.http.HttpServletResponse;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private static final URI PROBLEM_TYPE_DEFAULT = URI.create("about:blank");

    private final ObjectMapper objectMapper;

    public SecurityConfig(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Bean
    public BCryptPasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    @Order(2)
    public SecurityFilterChain apiChain(HttpSecurity http,
                                        SessionTokenAuthenticationFilter filter) throws Exception {
        http
                .securityMatcher("/api/**")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/login/**", "/api/v1/auth/refresh").permitAll()
                        .requestMatchers("/api/v1/backoffice/**").hasRole("CASE_WORKER")
                        .requestMatchers("/api/v1/applications/**", "/api/v1/documents/**",
                                "/api/v1/document/**", "/api/v1/auth/logout")
                        .hasAnyRole("COMPANY", "CASE_WORKER")
                        .anyRequest().authenticated())
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(authenticationEntryPoint())
                        .accessDeniedHandler(accessDeniedHandler()))
                .addFilterBefore(filter, UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    // TODO: Temporary to keep old version working
    @Bean
    @Order(3)
    public SecurityFilterChain webChain(HttpSecurity http) throws Exception {
        // Non-breaking: keep the old Thymeleaf/session app working as before.
        http
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                .csrf(csrf -> csrf.disable())
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable());
        return http.build();
    }

    /**
     * Permits unauthenticated access to Swagger UI and OpenAPI spec endpoints.
     * Only active on the "local" profile — never included in packaged builds.
     * Registered before {@link #webChain} (which matches any request) so it can
     * intercept swagger URLs first. Its matcher ({@code /v3/api-docs/**},
     * {@code /swagger-ui/**}) does not overlap {@code /api/**}, so API endpoints
     * are unaffected.
     */
    @Bean
    @Order(1)
    @Profile("local")
    public SecurityFilterChain swaggerChain(HttpSecurity http) throws Exception {
        http
                .securityMatcher("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
        return http.build();
    }

    private AuthenticationEntryPoint authenticationEntryPoint() {
        return (req, res, e) -> {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.UNAUTHORIZED, "Unauthorized");
            problemDetail.setTitle("Unauthorized");
            problemDetail.setType(PROBLEM_TYPE_DEFAULT);
            writeProblemDetail(res, HttpStatus.UNAUTHORIZED, problemDetail);
        };
    }

    private AccessDeniedHandler accessDeniedHandler() {
        return (req, res, e) -> {
            ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                    HttpStatus.FORBIDDEN, "Forbidden");
            problemDetail.setTitle("Access Denied");
            problemDetail.setType(PROBLEM_TYPE_DEFAULT);
            writeProblemDetail(res, HttpStatus.FORBIDDEN, problemDetail);
        };
    }

    private void writeProblemDetail(HttpServletResponse res, HttpStatus status, ProblemDetail problemDetail)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(res.getWriter(), problemDetail);
    }
}
