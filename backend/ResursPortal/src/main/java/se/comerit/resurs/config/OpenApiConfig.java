package se.comerit.resurs.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("local")
public class OpenApiConfig {

    @Bean
    public OpenAPI resursPortalOpenAPI() {
        final String securitySchemeName = "Bearer Authentication";
        return new OpenAPI()
                .info(new Info()
                        .title("Resurs Portal API")
                        .description("REST API for managing credit applications, "
                                + "document uploads, and case-worker decisions.")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("Resurs Team")))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("Session Token")));
    }
}
