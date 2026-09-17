package se.comerit.resurs.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import se.comerit.resurs.security.SessionCookie;

@Configuration
@Profile("local")
public class OpenApiConfig {

    /** Name of the security scheme used by the {@code @SecurityRequirement} annotations. */
    private static final String SECURITY_SCHEME_NAME = "Session Cookie";

    @Bean
    public OpenAPI resursPortalOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Resurs Portal API")
                        .description("REST API for managing credit applications, "
                                + "document uploads, and case-worker decisions.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Resurs Team")))
                .components(new Components()
                        .addSecuritySchemes(SECURITY_SCHEME_NAME,
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name(SessionCookie.ACCESS)
                                        .description("HttpOnly session cookie set by the login "
                                                + "endpoints. The server rotates the "
                                                + "access/refresh cookie pair transparently; "
                                                + "no Authorization header is used.")));
    }
}